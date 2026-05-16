package com.buildledger.finance.service.impl;

import com.buildledger.finance.event.NotificationEvent;
import com.buildledger.finance.event.NotificationProducer;
import com.buildledger.finance.dto.request.InvoiceRequestDTO;
import com.buildledger.finance.dto.request.PaymentRequestDTO;
import com.buildledger.finance.dto.response.*;
import com.buildledger.finance.entity.Invoice;
import com.buildledger.finance.entity.Payment;
import com.buildledger.finance.enums.InvoiceStatus;
import com.buildledger.finance.enums.PaymentStatus;
import com.buildledger.finance.feign.ContractServiceClient;
import com.buildledger.finance.feign.ContractServiceFallback;
import com.buildledger.finance.repository.InvoiceRepository;
import com.buildledger.finance.repository.PaymentRepository;
import com.buildledger.finance.exception.ResourceNotFoundException;
import com.buildledger.finance.exception.ServiceUnavailableException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j @Transactional
class FinanceServiceImpl implements com.buildledger.finance.service.FinanceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ContractServiceClient contractServiceClient;
    private final NotificationProducer notificationProducer;

    // ── Invoice ───────────────────────────────────────────────────────────────

    public InvoiceResponseDTO submitInvoice(InvoiceRequestDTO request) {
        log.info("Submitting invoice for contract {}", request.getContractId());
        Map<String, Object> contractData = validateContractExists(request.getContractId());

        // ← get vendor username from contract data
        String vendorUsername = String.valueOf(contractData.getOrDefault("vendorUsername", ""));
        String vendorName     = String.valueOf(contractData.getOrDefault("vendorName", "N/A"));

        Invoice invoice = Invoice.builder()
                .contractId(request.getContractId())
                .vendorName(vendorName)
                .vendorUsername(vendorUsername)   // ← store
                .amount(request.getAmount()).date(request.getDate())
                .dueDate(request.getDueDate()).description(request.getDescription())
                .status(InvoiceStatus.UNDER_REVIEW).build();

        InvoiceResponseDTO result = mapInvoiceToResponse(invoiceRepository.save(invoice));

        sendInvoiceNotif("INVOICE_SUBMITTED",
                "Invoice #" + result.getInvoiceId() + " submitted successfully",
                "Dear " + vendorName + ", your invoice #" + result.getInvoiceId()
                        + " for amount " + request.getAmount()
                        + " has been submitted and is now UNDER REVIEW.",
                String.valueOf(result.getInvoiceId()),
                vendorUsername, ADMIN_USERNAME);

        return result;
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDTO getInvoiceById(Long invoiceId) {
        return mapInvoiceToResponse(findInvoiceById(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getInvoicesByContract(Long contractId) {
        return invoiceRepository.findByContractId(contractId).stream()
                .map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getInvoicesByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status).stream()
                .map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    public InvoiceResponseDTO approveInvoice(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNDER_REVIEW) {
            throw new RuntimeException(
                    "Invoice can only be approved when UNDER_REVIEW. Current: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.APPROVED);
        InvoiceResponseDTO result = mapInvoiceToResponse(invoiceRepository.save(invoice));

        sendInvoiceNotif("INVOICE_APPROVED",
                "Your invoice #" + invoiceId + " has been approved",
                "Dear " + invoice.getVendorName() + ", your invoice #" + invoiceId
                        + " for amount " + invoice.getAmount()
                        + " has been APPROVED. Payment will be processed soon.",
                String.valueOf(invoiceId),
                invoice.getVendorUsername(), ADMIN_USERNAME);

        return result;
    }

    public InvoiceResponseDTO rejectInvoice(Long invoiceId, String reason) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNDER_REVIEW) {
            throw new RuntimeException(
                    "Invoice can only be rejected when UNDER_REVIEW. Current: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectionReason(reason);
        InvoiceResponseDTO result = mapInvoiceToResponse(invoiceRepository.save(invoice));

        sendInvoiceNotif("INVOICE_REJECTED",
                "Your invoice #" + invoiceId + " has been rejected",
                "Dear " + invoice.getVendorName() + ", your invoice #" + invoiceId
                        + " has been REJECTED. Reason: " + reason
                        + ". Please review and resubmit if needed.",
                String.valueOf(invoiceId),
                invoice.getVendorUsername(), ADMIN_USERNAME);

        return result;
    }

    public void deleteInvoice(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Cannot delete a PAID invoice.");
        }

        String vendorUsername = invoice.getVendorUsername();
        String vendorName     = invoice.getVendorName();

        invoiceRepository.delete(invoice);

        sendInvoiceNotif("INVOICE_DELETED",
                "Invoice #" + invoiceId + " has been deleted",
                "Dear " + vendorName + ", invoice #" + invoiceId
                        + " has been permanently deleted.",
                String.valueOf(invoiceId),
                vendorUsername, ADMIN_USERNAME);
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    public PaymentResponseDTO processPayment(PaymentRequestDTO request) {
        log.info("Processing payment for invoice {}", request.getInvoiceId());
        Invoice invoice = findInvoiceById(request.getInvoiceId());

        if (invoice.getStatus() != InvoiceStatus.APPROVED) {
            throw new RuntimeException(
                    "Payment can only be processed for APPROVED invoices. Current status: "
                            + invoice.getStatus());
        }

        Payment payment = Payment.builder()
                .invoice(invoice).amount(request.getAmount()).date(request.getDate())
                .method(request.getMethod()).status(PaymentStatus.PENDING)
                .transactionReference(request.getTransactionReference())
                .remarks(request.getRemarks()).build();

        PaymentResponseDTO result = mapPaymentToResponse(paymentRepository.save(payment));

        sendPaymentNotif("PAYMENT_INITIATED",
                "Payment initiated for your invoice #" + request.getInvoiceId(),
                "Dear " + invoice.getVendorName() + ", a payment of " + request.getAmount()
                        + " has been initiated for invoice #" + request.getInvoiceId()
                        + ". Payment method: " + request.getMethod()
                        + ". Transaction ref: " + request.getTransactionReference(),
                String.valueOf(result.getPaymentId()),
                invoice.getVendorUsername(), ADMIN_USERNAME);

        return result;
    }

    @Transactional(readOnly = true)
    public PaymentResponseDTO getPaymentById(Long paymentId) {
        return mapPaymentToResponse(findPaymentById(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getPaymentsByInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceInvoiceId(invoiceId).stream()
                .map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    public PaymentResponseDTO updatePaymentStatus(Long paymentId, PaymentStatus newStatus) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus current = payment.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new RuntimeException(
                    "Invalid payment status transition from " + current + " to " + newStatus +
                            ". Lifecycle: PENDING→PROCESSING|FAILED, PROCESSING→COMPLETED|FAILED.");
        }

        payment.setStatus(newStatus);

        if (newStatus == PaymentStatus.PROCESSING) {
            sendPaymentNotif("PAYMENT_PROCESSING",
                    "Your payment is being processed",
                    "Dear " + payment.getInvoice().getVendorName()
                            + ", your payment of " + payment.getAmount()
                            + " for invoice #" + payment.getInvoice().getInvoiceId()
                            + " is now being PROCESSED.",
                    String.valueOf(paymentId),
                    payment.getInvoice().getVendorUsername(), ADMIN_USERNAME);

        } else if (newStatus == PaymentStatus.FAILED) {
            sendPaymentNotif("PAYMENT_FAILED",
                    "Payment FAILED for invoice #" + payment.getInvoice().getInvoiceId(),
                    "Dear " + payment.getInvoice().getVendorName()
                            + ", your payment of " + payment.getAmount()
                            + " for invoice #" + payment.getInvoice().getInvoiceId()
                            + " has FAILED. Please contact support.",
                    String.valueOf(paymentId),
                    payment.getInvoice().getVendorUsername(), ADMIN_USERNAME);

        } else if (newStatus == PaymentStatus.COMPLETED) {
            // When payment COMPLETED → mark invoice as PAID
            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            log.info("Invoice {} marked PAID after payment {} completed",
                    invoice.getInvoiceId(), paymentId);

            sendPaymentNotif("PAYMENT_COMPLETED",
                    "Payment completed — invoice paid",
                    "Dear " + invoice.getVendorName()
                            + ", payment of " + payment.getAmount()
                            + " for invoice #" + invoice.getInvoiceId()
                            + " has been COMPLETED. Transaction ref: "
                            + payment.getTransactionReference(),
                    String.valueOf(paymentId),
                    invoice.getVendorUsername(), ADMIN_USERNAME);

            sendInvoiceNotif("INVOICE_PAID",
                    "Invoice #" + invoice.getInvoiceId() + " has been paid",
                    "Dear " + invoice.getVendorName()
                            + ", invoice #" + invoice.getInvoiceId()
                            + " for amount " + invoice.getAmount()
                            + " has been marked as PAID. Transaction ref: "
                            + payment.getTransactionReference(),
                    String.valueOf(invoice.getInvoiceId()),
                    invoice.getVendorUsername(), ADMIN_USERNAME);
        }

        return mapPaymentToResponse(paymentRepository.save(payment));
    }

    private static final String ADMIN_USERNAME = "admin";

    private void sendInvoiceNotif(String type, String subject, String message,
                                  String refId, String... recipients) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String r : recipients) { if (r != null && !r.isBlank()) seen.add(r); }
        for (String r : seen) {
            notificationProducer.send("invoice-events", NotificationEvent.builder()
                    .recipientEmail(r).recipientName(r)
                    .type(type).subject(subject).message(message)
                    .referenceId(refId).referenceType("INVOICE").build());
        }
    }

    private void sendPaymentNotif(String type, String subject, String message,
                                  String refId, String... recipients) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String r : recipients) { if (r != null && !r.isBlank()) seen.add(r); }
        for (String r : seen) {
            notificationProducer.send("payment-events", NotificationEvent.builder()
                    .recipientEmail(r).recipientName(r)
                    .type(type).subject(subject).message(message)
                    .referenceId(refId).referenceType("PAYMENT").build());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> validateContractExists(Long contractId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = contractServiceClient.getContractById(contractId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        } catch (Exception e) {
            throw new ServiceUnavailableException(
                    "Contract Service is currently unavailable. Please try again later.");
        }
        if (ContractServiceFallback.MARKER.equals(response.getMessage())) {
            throw new ServiceUnavailableException(
                    "Contract Service is currently unavailable. Please try again later.");
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        }
        return response.getData();
    }

    private Invoice findInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
    }

    private Payment findPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }

    private InvoiceResponseDTO mapInvoiceToResponse(Invoice i) {
        return InvoiceResponseDTO.builder()
                .invoiceId(i.getInvoiceId()).contractId(i.getContractId()).vendorName(i.getVendorName())
                .amount(i.getAmount()).date(i.getDate()).dueDate(i.getDueDate())
                .description(i.getDescription()).status(i.getStatus())
                .rejectionReason(i.getRejectionReason())
                .createdAt(i.getCreatedAt()).updatedAt(i.getUpdatedAt()).build();
    }

    private PaymentResponseDTO mapPaymentToResponse(Payment p) {
        return PaymentResponseDTO.builder()
                .paymentId(p.getPaymentId()).invoiceId(p.getInvoice().getInvoiceId())
                .amount(p.getAmount()).date(p.getDate()).method(p.getMethod()).status(p.getStatus())
                .transactionReference(p.getTransactionReference()).remarks(p.getRemarks())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();
    }
}
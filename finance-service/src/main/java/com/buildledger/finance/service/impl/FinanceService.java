package com.buildledger.finance.service.impl;

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
import com.buildledger.finance.exception.BadRequestException;
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

    // ── Invoice ───────────────────────────────────────────────────────────────

    public InvoiceResponseDTO submitInvoice(InvoiceRequestDTO request) {
        log.info("Submitting invoice for contract {}", request.getContractId());
        Map<String, Object> contractData = validateContractActive(request.getContractId());

        // Enforce invoice amount cap against contract value
        Object contractValueObj = contractData.get("value");
        if (contractValueObj != null) {
            java.math.BigDecimal contractValue = new java.math.BigDecimal(contractValueObj.toString());
            java.math.BigDecimal alreadyInvoiced = invoiceRepository.sumAmountByContractIdAndStatuses(
                request.getContractId(),
                java.util.List.of(InvoiceStatus.APPROVED, InvoiceStatus.PAID, InvoiceStatus.UNDER_REVIEW));
            if (alreadyInvoiced.add(request.getAmount()).compareTo(contractValue) > 0) {
                throw new BadRequestException(
                    "Invoice amount would exceed contract value. Contract value: " + contractValue +
                    ", already invoiced: " + alreadyInvoiced +
                    ", requested: " + request.getAmount() + ".");
            }
        }

        Invoice invoice = Invoice.builder()
            .contractId(request.getContractId())
            .vendorName((String) contractData.getOrDefault("vendorName", "N/A"))
            .amount(request.getAmount()).date(request.getDate())
            .dueDate(request.getDueDate()).description(request.getDescription())
            .status(InvoiceStatus.UNDER_REVIEW).build();

        return mapInvoiceToResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDTO getInvoiceById(Long invoiceId) {
        return mapInvoiceToResponse(findInvoiceById(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getAllInvoices() {
        return invoiceRepository.findAll().stream().map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getInvoicesByContract(Long contractId) {
        return invoiceRepository.findByContractId(contractId).stream().map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> getInvoicesByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status).stream().map(this::mapInvoiceToResponse).collect(Collectors.toList());
    }

    public InvoiceResponseDTO approveInvoice(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNDER_REVIEW) {
            throw new BadRequestException(
                "Invoice can only be approved when UNDER_REVIEW. Current: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.APPROVED);
        return mapInvoiceToResponse(invoiceRepository.save(invoice));
    }

    public InvoiceResponseDTO rejectInvoice(Long invoiceId, String reason) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNDER_REVIEW) {
            throw new BadRequestException(
                "Invoice can only be rejected when UNDER_REVIEW. Current: " + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.REJECTED);
        invoice.setRejectionReason(reason);
        return mapInvoiceToResponse(invoiceRepository.save(invoice));
    }

    public void deleteInvoice(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.UNDER_REVIEW && invoice.getStatus() != InvoiceStatus.REJECTED) {
            throw new BadRequestException(
                "Only UNDER_REVIEW or REJECTED invoices can be deleted. Current status: " + invoice.getStatus());
        }
        invoiceRepository.delete(invoice);
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    public PaymentResponseDTO processPayment(PaymentRequestDTO request) {
        log.info("Processing payment for invoice {}", request.getInvoiceId());
        Invoice invoice = findInvoiceById(request.getInvoiceId());

        if (invoice.getStatus() != InvoiceStatus.APPROVED) {
            throw new BadRequestException(
                "Payment can only be processed for APPROVED invoices. Current status: " + invoice.getStatus());
        }
        if (request.getAmount().compareTo(invoice.getAmount()) > 0) {
            throw new BadRequestException(
                "Payment amount " + request.getAmount() +
                " exceeds invoice amount " + invoice.getAmount() + ". Overpayment is not allowed.");
        }

        Payment payment = Payment.builder()
            .invoice(invoice).amount(request.getAmount()).date(request.getDate())
            .method(request.getMethod()).status(PaymentStatus.PENDING)
            .transactionReference(request.getTransactionReference()).remarks(request.getRemarks()).build();

        return mapPaymentToResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponseDTO getPaymentById(Long paymentId) {
        return mapPaymentToResponse(findPaymentById(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getAllPayments() {
        return paymentRepository.findAll().stream().map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getPaymentsByInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceInvoiceId(invoiceId).stream().map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    public PaymentResponseDTO updatePaymentStatus(Long paymentId, PaymentStatus newStatus) {
        Payment payment = findPaymentById(paymentId);
        PaymentStatus current = payment.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException(
                "Invalid payment status transition from " + current + " to " + newStatus +
                ". Lifecycle: PENDING→PROCESSING|FAILED, PROCESSING→COMPLETED|FAILED.");
        }

        payment.setStatus(newStatus);

        // When payment COMPLETED → mark invoice as PAID
        if (newStatus == PaymentStatus.COMPLETED) {
            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            log.info("Invoice {} marked PAID after payment {} completed", invoice.getInvoiceId(), paymentId);
        }

        return mapPaymentToResponse(paymentRepository.save(payment));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> validateContractActive(Long contractId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = contractServiceClient.getContractById(contractId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }
        if (ContractServiceFallback.MARKER.equals(response.getMessage())) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        }
        Map<String, Object> data = response.getData();
        String status = (String) data.get("status");
        if (!"ACTIVE".equals(status)) {
            throw new BadRequestException(
                "Invoices can only be submitted against ACTIVE contracts. Contract " + contractId +
                " is currently " + status + ".");
        }
        return data;
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
            .amount(i.getAmount()).date(i.getDate()).dueDate(i.getDueDate()).description(i.getDescription())
            .status(i.getStatus()).rejectionReason(i.getRejectionReason())
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


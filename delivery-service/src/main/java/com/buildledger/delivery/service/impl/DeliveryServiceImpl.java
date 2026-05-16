package com.buildledger.delivery.service.impl;

import com.buildledger.delivery.dto.response.ContractBudgetSummaryDTO;
import com.buildledger.delivery.event.NotificationEvent;
import com.buildledger.delivery.event.NotificationProducer;
import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.entity.Delivery;
import com.buildledger.delivery.entity.ServiceRecord;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.exception.BadRequestException;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.exception.ServiceUnavailableException;
import com.buildledger.delivery.feign.ContractServiceClient;
import com.buildledger.delivery.feign.ContractServiceFallback;
import com.buildledger.delivery.feign.VendorServiceClient;
import com.buildledger.delivery.feign.VendorServiceFallback;
import com.buildledger.delivery.repository.DeliveryRepository;
import com.buildledger.delivery.repository.ServiceRecordRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
class DeliveryServiceImpl implements com.buildledger.delivery.service.DeliveryService {

    private final DeliveryRepository       deliveryRepository;
    private final ServiceRecordRepository  serviceRecordRepository;
    private final ContractServiceClient    contractServiceClient;
    private final VendorServiceClient      vendorServiceClient;
    private final NotificationProducer     notificationProducer;

    // ── Create Delivery ───────────────────────────────────────────────────────

    public DeliveryResponseDTO createDelivery(DeliveryRequestDTO request) {
        log.info("Creating delivery for contract {}", request.getContractId());
        Map<String, Object> contractData = validateContractActive(request.getContractId());
        validateDeliveryDateInWindow(request.getDate(), contractData);
        validateVendorOwnership(contractData);

        // Extract cached usernames from contract data for scheduler notifications
        String managerUsername = (String) contractData.getOrDefault("managerUsername", "");
        String vendorUsername  = (String) contractData.getOrDefault("vendorUsername",  "");

        Delivery delivery = Delivery.builder()
                .contractId(request.getContractId())
                .date(request.getDate())
                .item(request.getItem())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .remarks(request.getRemarks())
                .managerUsername(managerUsername)
                .vendorUsername(vendorUsername)
                .build();

        DeliveryResponseDTO result = mapToResponse(deliveryRepository.save(delivery));

        String createSubject = "New delivery scheduled for contract #" + request.getContractId();
        String createMessage = "A new delivery has been scheduled for contract #" + request.getContractId()
                + ". Item: " + request.getItem()
                + ", Quantity: " + request.getQuantity() + " " + request.getUnit()
                + ", Expected date: " + request.getDate();
        String createRefId = String.valueOf(result.getDeliveryId());

        // Notify PM, Vendor, Admin
        sendDeliveryNotif("DELIVERY_CREATED", createSubject, createMessage, createRefId,
                managerUsername, vendorUsername, ADMIN_USERNAME);

        return result;
    }

    // ── Contract Budget Summary ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ContractBudgetSummaryDTO getContractBudgetSummary(Long contractId) {
        Map<String, Object> contractData = validateContractActive(contractId);

        // Get contract value from Feign response
        BigDecimal contractValue = extractBigDecimal(contractData.get("value"));

        List<Delivery>      deliveries = deliveryRepository.findByContractId(contractId);
        List<ServiceRecord> services   = serviceRecordRepository.findByContractId(contractId);

        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal remaining  = contractValue;

        return ContractBudgetSummaryDTO.builder()
                .contractId(contractId)
                .contractValue(contractValue)
                .spent(totalSpent)
                .remaining(remaining)
                .overBudget(remaining.compareTo(BigDecimal.ZERO) < 0)
                .deliveryCount(deliveries.size())
                .serviceCount(services.size())
                .build();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeliveryResponseDTO getDeliveryById(Long deliveryId) {
        return mapToResponse(findById(deliveryId));
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getAllDeliveries() {
        return deliveryRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponseDTO> getDeliveriesByContract(Long contractId) {
        return deliveryRepository.findByContractId(contractId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public DeliveryResponseDTO updateDeliveryStatus(Long deliveryId, DeliveryStatus nextStatus) {
        Delivery       delivery = findById(deliveryId);
        DeliveryStatus current  = delivery.getStatus();

        if (current == nextStatus) return mapToResponse(delivery);

        if (!current.canTransitionTo(nextStatus)) {
            throw new BadRequestException(
                    "Invalid status transition from " + current + " to " + nextStatus +
                            ". Allowed: PENDING→MARKED_DELIVERED|DELAYED, MARKED_DELIVERED→ACCEPTED|REJECTED, DELAYED→MARKED_DELIVERED.");
        }

        if (nextStatus == DeliveryStatus.ACCEPTED || nextStatus == DeliveryStatus.REJECTED)
            requireRole("PROJECT_MANAGER", "ADMIN");
        if (nextStatus == DeliveryStatus.MARKED_DELIVERED || nextStatus == DeliveryStatus.DELAYED)
            requireRole("VENDOR", "ADMIN");

        delivery.setStatus(nextStatus);
        DeliveryResponseDTO result = mapToResponse(deliveryRepository.save(delivery));

        String type, subject, message;
        if (nextStatus == DeliveryStatus.ACCEPTED) {
            type = "DELIVERY_ACCEPTED"; subject = "Your delivery has been accepted";
            message = "Your delivery #" + deliveryId + " for item '" + delivery.getItem() + "' has been ACCEPTED.";
        } else if (nextStatus == DeliveryStatus.REJECTED) {
            type = "DELIVERY_REJECTED"; subject = "Your delivery has been rejected";
            message = "Your delivery #" + deliveryId + " for item '" + delivery.getItem() + "' has been REJECTED.";
        } else if (nextStatus == DeliveryStatus.MARKED_DELIVERED) {
            type = "DELIVERY_MARKED_DELIVERED"; subject = "Delivery #" + deliveryId + " marked as delivered";
            message = "Delivery #" + deliveryId + " for contract #" + delivery.getContractId()
                    + " has been marked as delivered. Item: " + delivery.getItem()
                    + ". Awaiting acceptance from Project Manager.";
        } else {
            type = "DELIVERY_DELAYED"; subject = "Delivery #" + deliveryId + " marked as delayed";
            message = "Delivery #" + deliveryId + " has been marked as DELAYED.";
        }

        // Route to the correct recipient based on who needs to know about this status change:
        // ACCEPTED/REJECTED → vendor (PM acted, vendor needs to know)
        // MARKED_DELIVERED/DELAYED → PM (vendor acted, PM needs to know)
        String notifyRecipient = (nextStatus == DeliveryStatus.ACCEPTED || nextStatus == DeliveryStatus.REJECTED)
                ? delivery.getVendorUsername()
                : delivery.getManagerUsername();

        // Notify the relevant party + admin
        sendDeliveryNotif(type, subject, message, String.valueOf(deliveryId),
                notifyRecipient, ADMIN_USERNAME);

        return result;
    }

    public DeliveryResponseDTO updateDelivery(Long deliveryId, DeliveryRequestDTO request) {
        Delivery delivery = findById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING)
            throw new BadRequestException("Delivery details can only be updated when status is PENDING.");

        Long targetContractId = request.getContractId() != null ? request.getContractId() : delivery.getContractId();

        if (request.getContractId() != null) {
            validateContractActive(request.getContractId());
            delivery.setContractId(request.getContractId());
        }
        if (request.getDate() != null) {
            Map<String, Object> contractData = validateContractActive(targetContractId);
            validateDeliveryDateInWindow(request.getDate(), contractData);
            delivery.setDate(request.getDate());
        }
        if (request.getItem()     != null) delivery.setItem(request.getItem());
        if (request.getQuantity() != null) delivery.setQuantity(request.getQuantity());
        if (request.getUnit()     != null) delivery.setUnit(request.getUnit());
        if (request.getRemarks()  != null) delivery.setRemarks(request.getRemarks());

        DeliveryResponseDTO updated = mapToResponse(deliveryRepository.save(delivery));

        String updSubject = "Delivery #" + deliveryId + " details updated";
        String updMessage = "Delivery #" + deliveryId + " for contract #" + delivery.getContractId()
                + " has been updated. Item: " + delivery.getItem() + ", Date: " + delivery.getDate() + ".";
        sendDeliveryNotif("DELIVERY_UPDATED", updSubject, updMessage, String.valueOf(deliveryId),
                delivery.getManagerUsername(), delivery.getVendorUsername(), ADMIN_USERNAME);

        return updated;
    }

    public void deleteDelivery(Long deliveryId) {
        Delivery delivery = findById(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING)
            throw new BadRequestException("Only PENDING deliveries can be deleted.");
        deliveryRepository.delete(delivery);

        String delSubject = "Delivery #" + deliveryId + " has been deleted";
        String delMessage = "Delivery #" + deliveryId + " for contract #" + delivery.getContractId()
                + " (Item: " + delivery.getItem() + ") has been permanently deleted.";
        sendDeliveryNotif("DELIVERY_DELETED", delSubject, delMessage, String.valueOf(deliveryId),
                delivery.getManagerUsername(), delivery.getVendorUsername(), ADMIN_USERNAME);
    }

    // ── Notification helper ──────────────────────────────────────────────────

    private static final String ADMIN_USERNAME = "admin";

    private void sendDeliveryNotif(String type, String subject, String message, String refId,
                                   String... recipients) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String r : recipients) {
            if (r != null && !r.isBlank()) seen.add(r);
        }
        for (String r : seen) {
            notificationProducer.send("delivery-events", NotificationEvent.builder()
                    .recipientEmail(r).recipientName(r)
                    .type(type).subject(subject).message(message)
                    .referenceId(refId).referenceType("DELIVERY").build());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> validateContractActive(Long contractId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = contractServiceClient.getContractById(contractId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable.");
        }
        if (ContractServiceFallback.MARKER.equals(response.getMessage()))
            throw new ServiceUnavailableException("Contract Service is currently unavailable.");
        if (!response.isSuccess() || response.getData() == null)
            throw new ResourceNotFoundException("Contract", "id", contractId);

        Map<String, Object> data   = response.getData();
        String              status = (String) data.get("status");
        if (!"ACTIVE".equals(status))
            throw new BadRequestException(
                    "Deliveries can only be logged against ACTIVE contracts. Contract " + contractId
                            + " is currently " + status + ".");
        return data;
    }

    private void validateDeliveryDateInWindow(java.time.LocalDate deliveryDate, Map<String, Object> contractData) {
        if (deliveryDate == null) return;
        Object startObj = contractData.get("startDate");
        Object endObj   = contractData.get("endDate");
        if (startObj == null || endObj == null) return;
        java.time.LocalDate contractStart = java.time.LocalDate.parse(startObj.toString());
        java.time.LocalDate contractEnd   = java.time.LocalDate.parse(endObj.toString());
        if (deliveryDate.isBefore(contractStart) || deliveryDate.isAfter(contractEnd))
            throw new BadRequestException(
                    "Delivery date " + deliveryDate + " is outside the contract period ("
                            + contractStart + " to " + contractEnd + ").");
    }

    private void validateVendorOwnership(Map<String, Object> contractData) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        boolean isVendor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
        if (!isVendor) return;

        Long authenticatedUserId = (Long) auth.getCredentials();
        if (authenticatedUserId == null) return;

        Object vendorIdObj     = contractData.get("vendorId");
        if (vendorIdObj == null) return;
        Long contractVendorId = vendorIdObj instanceof Integer
                ? ((Integer) vendorIdObj).longValue() : ((Number) vendorIdObj).longValue();

        ApiResponseDTO<Map<String, Object>> vendorResponse;
        try {
            vendorResponse = vendorServiceClient.getVendorById(contractVendorId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Vendor", "id", contractVendorId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        }
        if (VendorServiceFallback.MARKER.equals(vendorResponse.getMessage()))
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        if (!vendorResponse.isSuccess() || vendorResponse.getData() == null)
            throw new ResourceNotFoundException("Vendor", "id", contractVendorId);

        Object userIdObj  = vendorResponse.getData().get("userId");
        if (userIdObj == null) return;
        Long vendorUserId = userIdObj instanceof Integer
                ? ((Integer) userIdObj).longValue() : ((Number) userIdObj).longValue();
        if (!authenticatedUserId.equals(vendorUserId))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied: you do not own the vendor associated with this contract.");
    }

    private void requireRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> { for (String r : roles) if (a.getAuthority().equals("ROLE_" + r)) return true; return false; });
        if (!hasRole)
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied. Required roles: " + String.join(" or ", roles));
    }

    private BigDecimal extractBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try { return new BigDecimal(value.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Delivery findById(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", id));
    }

    private DeliveryResponseDTO mapToResponse(Delivery d) {
        return DeliveryResponseDTO.builder()
                .deliveryId(d.getDeliveryId()).contractId(d.getContractId())
                .date(d.getDate()).item(d.getItem()).quantity(d.getQuantity()).unit(d.getUnit())
                .remarks(d.getRemarks()).status(d.getStatus())
                .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt()).build();
    }
}
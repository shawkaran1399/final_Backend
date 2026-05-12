package com.buildledger.delivery.service.impl;

import com.buildledger.delivery.dto.request.ServiceRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.ServiceResponseDTO;
import com.buildledger.delivery.entity.ServiceRecord;
import com.buildledger.delivery.enums.ServiceStatus;
import com.buildledger.delivery.event.NotificationEvent;
import com.buildledger.delivery.event.NotificationProducer;
import com.buildledger.delivery.exception.BadRequestException;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.exception.ServiceUnavailableException;
import com.buildledger.delivery.feign.ContractServiceClient;
import com.buildledger.delivery.feign.ContractServiceFallback;
import com.buildledger.delivery.feign.VendorServiceClient;
import com.buildledger.delivery.feign.VendorServiceFallback;
import com.buildledger.delivery.repository.DeliveryRepository;
import com.buildledger.delivery.repository.ServiceRecordRepository;
import com.buildledger.delivery.service.ServiceTrackingService;
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
class ServiceTrackingServiceImpl implements ServiceTrackingService {

    private final ServiceRecordRepository  serviceRecordRepository;
    private final DeliveryRepository       deliveryRepository;
    private final ContractServiceClient    contractServiceClient;
    private final VendorServiceClient      vendorServiceClient;
    private final NotificationProducer     notificationProducer;

    // ── Create Service ────────────────────────────────────────────────────────

    public ServiceResponseDTO createService(ServiceRequestDTO request) {
        log.info("Creating service for contract {}", request.getContractId());

        Map<String, Object> contractData = validateContractActive(request.getContractId());
        validateVendorOwnership(contractData);

        // ── Budget validation ─────────────────────────────────────────────────
        // Service price must not exceed remaining contract budget
        validateContractBudget(request.getContractId(), request.getPrice(), contractData);

        // Extract cached usernames from contract data for scheduler notifications
        String managerUsername = (String) contractData.getOrDefault("managerUsername", "");
        String vendorUsername  = (String) contractData.getOrDefault("vendorUsername",  "");

        ServiceRecord service = ServiceRecord.builder()
                .contractId(request.getContractId())
                .description(request.getDescription())
                .completionDate(request.getCompletionDate())
                .price(request.getPrice())               // ← FIX: was missing, caused NULL error
                .remarks(request.getRemarks())
                .managerUsername(managerUsername)
                .vendorUsername(vendorUsername)
                .build();

        ServiceResponseDTO result = mapToResponse(serviceRecordRepository.save(service));

        notificationProducer.send("delivery-events", NotificationEvent.builder()
                .recipientEmail("").recipientName("Admin")
                .type("SERVICE_CREATED")
                .subject("New service scheduled for contract #" + request.getContractId())
                .message("A new service has been scheduled for contract #" + request.getContractId()
                        + ". Description: " + request.getDescription()
                        + ". Price: ₹" + request.getPrice()
                        + ". Expected completion: " + request.getCompletionDate())
                .referenceId(String.valueOf(result.getServiceId()))
                .referenceType("SERVICE").build());

        return result;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ServiceResponseDTO getServiceById(Long serviceId) {
        return mapToResponse(findById(serviceId));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getAllServices() {
        return serviceRecordRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getServicesByContract(Long contractId) {
        validateContractActive(contractId);
        return serviceRecordRepository.findByContractId(contractId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getServicesByStatus(ServiceStatus status) {
        return serviceRecordRepository.findByStatus(status).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public ServiceResponseDTO updateServiceStatus(Long serviceId, ServiceStatus nextStatus) {
        ServiceRecord service = findById(serviceId);
        ServiceStatus current = service.getStatus();

        if (current == nextStatus) return mapToResponse(service);

        if (!current.canTransitionTo(nextStatus)) {
            throw new BadRequestException(
                    "Invalid status transition from " + current + " to " + nextStatus +
                            ". Allowed: PENDING→IN_PROGRESS, IN_PROGRESS→COMPLETED, COMPLETED→VERIFIED.");
        }

        if (nextStatus == ServiceStatus.IN_PROGRESS || nextStatus == ServiceStatus.COMPLETED)
            requireRole("VENDOR", "ADMIN");
        if (nextStatus == ServiceStatus.VERIFIED)
            requireRole("PROJECT_MANAGER", "ADMIN");

        service.setStatus(nextStatus);
        ServiceResponseDTO result = mapToResponse(serviceRecordRepository.save(service));

        String type, subject, message;
        if (nextStatus == ServiceStatus.COMPLETED) {
            type = "SERVICE_COMPLETED"; subject = "Service marked as completed";
            message = "Service #" + serviceId + " for contract #" + service.getContractId()
                    + " has been marked as COMPLETED. Awaiting verification.";
        } else if (nextStatus == ServiceStatus.VERIFIED) {
            type = "SERVICE_VERIFIED"; subject = "Service verified";
            message = "Service #" + serviceId + " for contract #" + service.getContractId()
                    + " has been VERIFIED by the project manager.";
        } else {
            type = "SERVICE_STATUS_UPDATED"; subject = "Service status updated";
            message = "Service #" + serviceId + " status: " + current + " → " + nextStatus;
        }

        notificationProducer.send("delivery-events", NotificationEvent.builder()
                .recipientEmail("").recipientName("Admin")
                .type(type).subject(subject).message(message)
                .referenceId(String.valueOf(serviceId)).referenceType("SERVICE").build());

        return result;
    }

    public ServiceResponseDTO updateService(Long serviceId, ServiceRequestDTO request) {
        ServiceRecord service = findById(serviceId);
        if (service.getStatus() != ServiceStatus.PENDING)
            throw new BadRequestException("Service can only be updated when status is PENDING.");

        if (request.getContractId()    != null) {
            validateContractActive(request.getContractId());
            service.setContractId(request.getContractId());
        }
        if (request.getDescription()   != null) service.setDescription(request.getDescription());
        if (request.getCompletionDate() != null) service.setCompletionDate(request.getCompletionDate());
        if (request.getPrice()         != null) {
            Map<String, Object> contractData = validateContractActive(service.getContractId());
            validateContractBudget(service.getContractId(), request.getPrice(), contractData);
            service.setPrice(request.getPrice());
        }
        if (request.getRemarks()       != null) service.setRemarks(request.getRemarks());

        return mapToResponse(serviceRecordRepository.save(service));
    }

    public void deleteService(Long serviceId) {
        ServiceRecord service = findById(serviceId);
        if (service.getStatus() != ServiceStatus.PENDING)
            throw new BadRequestException("Only PENDING services can be deleted.");
        serviceRecordRepository.delete(service);
    }

    // ── Budget validation ─────────────────────────────────────────────────────

    private void validateContractBudget(Long contractId, BigDecimal newPrice,
                                        Map<String, Object> contractData) {
        if (newPrice == null) return;

        BigDecimal contractValue = extractBigDecimal(contractData.get("value"));
        if (contractValue == null || contractValue.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal deliverySpent = deliveryRepository.findByContractId(contractId).stream()
                .map(d -> d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceSpent = serviceRecordRepository.findByContractId(contractId).stream()
                .map(s -> s.getPrice() != null ? s.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = contractValue.subtract(deliverySpent.add(serviceSpent));

        if (newPrice.compareTo(remaining) > 0) {
            throw new BadRequestException(
                    "Service price (₹" + newPrice + ") exceeds remaining contract budget (₹" + remaining + "). "
                            + "Contract value: ₹" + contractValue
                            + ", Already allocated: ₹" + deliverySpent.add(serviceSpent) + ".");
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
                    "Services can only be logged against ACTIVE contracts. Contract " + contractId
                            + " is currently " + status + ".");
        return data;
    }

    private void validateVendorOwnership(Map<String, Object> contractData) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        boolean isVendor = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"));
        if (!isVendor) return;

        Long authenticatedUserId = (Long) auth.getCredentials();
        if (authenticatedUserId == null) return;

        Object vendorIdObj = contractData.get("vendorId");
        if (vendorIdObj == null) return;
        Long contractVendorId = vendorIdObj instanceof Integer
                ? ((Integer) vendorIdObj).longValue() : ((Number) vendorIdObj).longValue();

        ApiResponseDTO<Map<String, Object>> vendorResponse;
        try {
            vendorResponse = vendorServiceClient.getVendorById(contractVendorId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        }
        if (VendorServiceFallback.MARKER.equals(vendorResponse.getMessage()))
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        if (!vendorResponse.isSuccess() || vendorResponse.getData() == null)
            throw new ResourceNotFoundException("Vendor", "id", contractVendorId);

        Object userIdObj = vendorResponse.getData().get("userId");
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

    private ServiceRecord findById(Long id) {
        return serviceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
    }

    private ServiceResponseDTO mapToResponse(ServiceRecord s) {
        return ServiceResponseDTO.builder()
                .serviceId(s.getServiceId()).contractId(s.getContractId())
                .description(s.getDescription()).completionDate(s.getCompletionDate())
                .price(s.getPrice()).status(s.getStatus()).remarks(s.getRemarks())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt()).build();
    }
}
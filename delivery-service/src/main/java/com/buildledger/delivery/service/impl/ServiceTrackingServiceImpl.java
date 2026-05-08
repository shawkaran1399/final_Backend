package com.buildledger.delivery.service.impl;

import com.buildledger.delivery.dto.request.ServiceRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.ServiceResponseDTO;
import com.buildledger.delivery.entity.ServiceRecord;
import com.buildledger.delivery.enums.ServiceStatus;
import com.buildledger.delivery.exception.BadRequestException;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.exception.ServiceUnavailableException;
import com.buildledger.delivery.feign.ContractServiceClient;
import com.buildledger.delivery.feign.ContractServiceFallback;
import com.buildledger.delivery.feign.VendorServiceClient;
import com.buildledger.delivery.feign.VendorServiceFallback;
import com.buildledger.delivery.repository.ServiceRecordRepository;
import com.buildledger.delivery.service.ServiceTrackingService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
class ServiceTrackingServiceImpl implements ServiceTrackingService {

    private final ServiceRecordRepository serviceRecordRepository;
    private final ContractServiceClient contractServiceClient;
    private final VendorServiceClient vendorServiceClient;

    @Override
    public ServiceResponseDTO createService(ServiceRequestDTO request) {
        log.info("Creating service record for contract {}", request.getContractId());

        Map<String, Object> contractData = validateContractActive(request.getContractId());
        validateServiceDateInWindow(request.getCompletionDate(), contractData);
        validateVendorOwnership(contractData);

        ServiceRecord record = ServiceRecord.builder()
                .contractId(request.getContractId())
                .description(request.getDescription())
                .completionDate(request.getCompletionDate())
                .remarks(request.getRemarks())
                .vendorUsername(String.valueOf(contractData.getOrDefault("vendorUsername", "")))
                .managerUsername(String.valueOf(contractData.getOrDefault("managerUsername", "")))
                .build();

        return mapToResponse(serviceRecordRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponseDTO getServiceById(Long serviceId) {
        return mapToResponse(findById(serviceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getAllServices() {
        return serviceRecordRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getServicesByContract(Long contractId) {
        validateContractExists(contractId);
        return serviceRecordRepository.findByContractId(contractId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getServicesByStatus(ServiceStatus status) {
        return serviceRecordRepository.findByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponseDTO updateServiceStatus(Long serviceId, ServiceStatus nextStatus) {
        ServiceRecord service = findById(serviceId);
        ServiceStatus current = service.getStatus();

        if (current == nextStatus) return mapToResponse(service);

        if (!current.canTransitionTo(nextStatus)) {
            throw new BadRequestException(
                    "Invalid service status transition from " + current + " to " + nextStatus +
                    ". Lifecycle must follow: PENDING → IN_PROGRESS → COMPLETED → VERIFIED/UNVERIFIED.");
        }

        if (nextStatus == ServiceStatus.IN_PROGRESS || nextStatus == ServiceStatus.COMPLETED) {
            requireRole("VENDOR", "ADMIN");
        }

        if (nextStatus == ServiceStatus.VERIFIED || nextStatus == ServiceStatus.UNVERIFIED) {
            requireRole("PROJECT_MANAGER", "ADMIN");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) service.setManagerUsername(auth.getName());
        }

        service.setStatus(nextStatus);
        return mapToResponse(serviceRecordRepository.save(service));
    }

    @Override
    public ServiceResponseDTO updateService(Long serviceId, ServiceRequestDTO request) {
        ServiceRecord service = findById(serviceId);

        if (service.getStatus() != ServiceStatus.PENDING) {
            throw new BadRequestException("Service details can only be updated when status is PENDING.");
        }

        Map<String, Object> contractData = null;

        if (request.getContractId() != null) {
            contractData = validateContractActive(request.getContractId());
            service.setContractId(request.getContractId());
        }

        if (request.getCompletionDate() != null) {
            if (contractData == null) {
                contractData = validateContractActive(service.getContractId());
            }
            validateServiceDateInWindow(request.getCompletionDate(), contractData);
            service.setCompletionDate(request.getCompletionDate());
        }

        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getRemarks() != null)     service.setRemarks(request.getRemarks());

        return mapToResponse(serviceRecordRepository.save(service));
    }

    @Override
    public void deleteService(Long serviceId) {
        ServiceRecord service = findById(serviceId);
        if (service.getStatus() != ServiceStatus.PENDING) {
            throw new BadRequestException("Only PENDING service records can be deleted.");
        }
        serviceRecordRepository.delete(service);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> fetchContract(Long contractId) {
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
        return response.getData();
    }

    private Map<String, Object> validateContractActive(Long contractId) {
        Map<String, Object> data = fetchContract(contractId);
        String status = (String) data.get("status");
        if (!"ACTIVE".equals(status)) {
            throw new BadRequestException(
                    "Service records can only be logged against ACTIVE contracts. Contract " +
                    contractId + " is currently " + status + ".");
        }
        return data;
    }

    private void validateContractExists(Long contractId) {
        fetchContract(contractId);
    }

    private void validateServiceDateInWindow(LocalDate completionDate, Map<String, Object> contractData) {
        if (completionDate == null) return;

        Object endObj = contractData.get("endDate");
        if (endObj == null) return;

        LocalDate today       = LocalDate.now();
        LocalDate contractEnd = LocalDate.parse(endObj.toString());

        if (completionDate.isBefore(today) || completionDate.isAfter(contractEnd)) {
            throw new BadRequestException(
                    "Completion date " + completionDate + " must be today or a future date within the contract end date (" +
                    contractEnd + ").");
        }
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
        Long contractVendorId = ((Number) vendorIdObj).longValue();

        ApiResponseDTO<Map<String, Object>> vendorResponse;
        try {
            vendorResponse = vendorServiceClient.getVendorById(contractVendorId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Vendor", "id", contractVendorId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable. Please try again later.");
        }

        if (VendorServiceFallback.MARKER.equals(vendorResponse.getMessage())) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable. Please try again later.");
        }
        if (!vendorResponse.isSuccess() || vendorResponse.getData() == null) {
            throw new ResourceNotFoundException("Vendor", "id", contractVendorId);
        }

        Object userIdObj = vendorResponse.getData().get("userId");
        if (userIdObj == null) return;
        Long vendorUserId = ((Number) userIdObj).longValue();

        if (!authenticatedUserId.equals(vendorUserId)) {
            throw new AccessDeniedException("Access denied: you do not own the vendor associated with this contract.");
        }
    }

    private void requireRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new AccessDeniedException("Not authenticated");

        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    for (String r : roles) {
                        if (a.getAuthority().equals("ROLE_" + r)) return true;
                    }
                    return false;
                });

        if (!hasRole) {
            throw new AccessDeniedException("Access denied. Required roles: " + String.join(" or ", roles));
        }
    }

    private ServiceRecord findById(Long id) {
        return serviceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceRecord", "id", id));
    }

    private ServiceResponseDTO mapToResponse(ServiceRecord s) {
        return ServiceResponseDTO.builder()
                .serviceId(s.getServiceId())
                .contractId(s.getContractId())
                .description(s.getDescription())
                .completionDate(s.getCompletionDate())
                .status(s.getStatus())
                .remarks(s.getRemarks())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}

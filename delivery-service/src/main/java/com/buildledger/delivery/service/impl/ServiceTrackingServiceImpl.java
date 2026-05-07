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
import com.buildledger.delivery.repository.ServiceRecordRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.buildledger.delivery.service.ServiceTrackingService;

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

    public ServiceResponseDTO createService(ServiceRequestDTO request) {
        log.info("Creating service record for contract {}", request.getContractId());
        validateContractExists(request.getContractId());

        ServiceRecord record = ServiceRecord.builder()
            .contractId(request.getContractId())
            .description(request.getDescription())
            .completionDate(request.getCompletionDate())
            .remarks(request.getRemarks())
            .build();

        return mapToResponse(serviceRecordRepository.save(record));
    }

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
        validateContractExists(contractId);
        return serviceRecordRepository.findByContractId(contractId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ServiceResponseDTO updateServiceStatus(Long serviceId, ServiceStatus nextStatus) {
        ServiceRecord service = findById(serviceId);
        ServiceStatus current = service.getStatus();

        if (current == nextStatus) return mapToResponse(service);

        if (!current.canTransitionTo(nextStatus)) {
            throw new BadRequestException(
                "Invalid service status transition from " + current + " to " + nextStatus +
                ". Lifecycle must follow: PENDING → IN_PROGRESS → COMPLETED → VERIFIED.");
        }

        // Role-based validation
        if (nextStatus == ServiceStatus.IN_PROGRESS || nextStatus == ServiceStatus.COMPLETED) {
            requireRole("VENDOR", "ADMIN");
        }
        if (nextStatus == ServiceStatus.VERIFIED) {
            requireRole("PROJECT_MANAGER", "ADMIN");
        }

        service.setStatus(nextStatus);
        return mapToResponse(serviceRecordRepository.save(service));
    }

    public ServiceResponseDTO updateService(Long serviceId, ServiceRequestDTO request) {
        ServiceRecord service = findById(serviceId);
        if (service.getStatus() != ServiceStatus.PENDING) {
            throw new BadRequestException("Service details can only be updated when status is PENDING.");
        }
        if (request.getContractId() != null) {
            validateContractExists(request.getContractId());
            service.setContractId(request.getContractId());
        }
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getCompletionDate() != null) service.setCompletionDate(request.getCompletionDate());
        if (request.getRemarks() != null) service.setRemarks(request.getRemarks());
        return mapToResponse(serviceRecordRepository.save(service));
    }

    public void deleteService(Long serviceId) {
        ServiceRecord service = findById(serviceId);
        if (service.getStatus() != ServiceStatus.PENDING) {
            throw new BadRequestException("Only PENDING service records can be deleted.");
        }
        serviceRecordRepository.delete(service);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateContractExists(Long contractId) {
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
    }

    private void requireRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        boolean hasRole = auth.getAuthorities().stream()
            .anyMatch(a -> {
                for (String r : roles) if (a.getAuthority().equals("ROLE_" + r)) return true;
                return false;
            });
        if (!hasRole) throw new org.springframework.security.access.AccessDeniedException(
            "Access denied. Required roles: " + String.join(" or ", roles));
    }

    private ServiceRecord findById(Long id) {
        return serviceRecordRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ServiceRecord", "id", id));
    }

    private ServiceResponseDTO mapToResponse(ServiceRecord s) {
        return ServiceResponseDTO.builder()
            .serviceId(s.getServiceId()).contractId(s.getContractId())
            .description(s.getDescription()).completionDate(s.getCompletionDate())
            .status(s.getStatus()).remarks(s.getRemarks())
            .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt()).build();
    }
}


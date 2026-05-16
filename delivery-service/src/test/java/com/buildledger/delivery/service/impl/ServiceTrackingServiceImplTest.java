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
import com.buildledger.delivery.repository.ServiceRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTrackingServiceImplTest {

    @Mock private ServiceRecordRepository serviceRecordRepository;
    @Mock private ContractServiceClient contractServiceClient;
    @Mock private VendorServiceClient vendorServiceClient;
    @Mock private NotificationProducer notificationProducer;

    @InjectMocks private ServiceTrackingServiceImpl serviceTrackingService;

    // ── Security Context Helpers ──────────────────────────────────────────────

    private void setAdminInContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    private void setProjectManagerInContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "manager1", null, List.of(new SimpleGrantedAuthority("ROLE_PROJECT_MANAGER")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    private void setVendorInContext() {
        // null credentials → validateVendorOwnership skips ownership check
        var auth = new UsernamePasswordAuthenticationToken(
                "vendor1", null, List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Test Data Helpers ────────────────────────────────────────────────────

    private ApiResponseDTO<Map<String, Object>> activeContractResponse() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "ACTIVE");
        data.put("startDate", "2024-01-01");
        data.put("endDate", "2025-12-31");
        data.put("managerUsername", "manager1");
        data.put("vendorUsername", "vendor1");
        data.put("vendorId", 10);
        return ApiResponseDTO.<Map<String, Object>>builder()
                .success(true).message("Contract retrieved").data(data).build();
    }

    private ServiceRecord pendingServiceRecord() {
        return ServiceRecord.builder()
                .serviceId(1L)
                .contractId(1L)
                .description("Install electrical wiring and fixtures")
                .completionDate(LocalDate.of(2024, 8, 30))
                .status(ServiceStatus.PENDING)
                .remarks("Standard installation")
                .managerUsername("manager1")
                .vendorUsername("vendor1")
                .build();
    }

    private ServiceRequestDTO serviceRequest() {
        ServiceRequestDTO req = new ServiceRequestDTO();
        req.setContractId(1L);
        req.setDescription("Install electrical wiring and fixtures");
        req.setCompletionDate(LocalDate.of(2024, 8, 30));
        req.setRemarks("Standard installation");
        return req;
    }

    // ============================================================
    // createService tests
    // ============================================================

    @Test
    void createService_withValidData_returnsServiceDTO() {
        setAdminInContext();
        when(contractServiceClient.getContractById(1L)).thenReturn(activeContractResponse());
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenReturn(pendingServiceRecord());

        ServiceResponseDTO result = serviceTrackingService.createService(serviceRequest());

        assertThat(result).isNotNull();
        assertThat(result.getServiceId()).isEqualTo(1L);
        assertThat(result.getDescription()).isEqualTo("Install electrical wiring and fixtures");
        assertThat(result.getStatus()).isEqualTo(ServiceStatus.PENDING);
        verify(serviceRecordRepository).save(any(ServiceRecord.class));
        verify(notificationProducer, atLeastOnce()).send(eq("delivery-events"), any(NotificationEvent.class));
    }

    @Test
    void createService_withInactiveContract_throwsBadRequestException() {
        setAdminInContext();
        Map<String, Object> data = new HashMap<>();
        data.put("status", "EXPIRED");
        var response = ApiResponseDTO.<Map<String, Object>>builder()
                .success(true).message("ok").data(data).build();
        when(contractServiceClient.getContractById(1L)).thenReturn(response);

        assertThatThrownBy(() -> serviceTrackingService.createService(serviceRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createService_whenContractServiceUnavailable_throwsServiceUnavailableException() {
        setAdminInContext();
        var fallback = ApiResponseDTO.<Map<String, Object>>builder()
                .success(false).message(ContractServiceFallback.MARKER).build();
        when(contractServiceClient.getContractById(1L)).thenReturn(fallback);

        assertThatThrownBy(() -> serviceTrackingService.createService(serviceRequest()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void createService_whenContractNotFound_throwsResourceNotFoundException() {
        setAdminInContext();
        var notFound = ApiResponseDTO.<Map<String, Object>>builder()
                .success(false).message("Not found").build();
        when(contractServiceClient.getContractById(1L)).thenReturn(notFound);

        assertThatThrownBy(() -> serviceTrackingService.createService(serviceRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // getServiceById tests
    // ============================================================

    @Test
    void getServiceById_existingId_returnsServiceDTO() {
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(pendingServiceRecord()));

        ServiceResponseDTO result = serviceTrackingService.getServiceById(1L);

        assertThat(result.getServiceId()).isEqualTo(1L);
        assertThat(result.getDescription()).isEqualTo("Install electrical wiring and fixtures");
        assertThat(result.getContractId()).isEqualTo(1L);
    }

    @Test
    void getServiceById_nonExistingId_throwsResourceNotFoundException() {
        when(serviceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTrackingService.getServiceById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // getAllServices tests
    // ============================================================

    @Test
    void getAllServices_returnsAllServices() {
        when(serviceRecordRepository.findAll()).thenReturn(List.of(pendingServiceRecord(), pendingServiceRecord()));

        List<ServiceResponseDTO> result = serviceTrackingService.getAllServices();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllServices_whenEmpty_returnsEmptyList() {
        when(serviceRecordRepository.findAll()).thenReturn(List.of());

        List<ServiceResponseDTO> result = serviceTrackingService.getAllServices();

        assertThat(result).isEmpty();
    }

    // ============================================================
    // getServicesByContract tests
    // ============================================================

    @Test
    void getServicesByContract_returnsServicesForContract() {
        when(contractServiceClient.getContractById(1L)).thenReturn(activeContractResponse());
        when(serviceRecordRepository.findByContractId(1L)).thenReturn(List.of(pendingServiceRecord()));

        List<ServiceResponseDTO> result = serviceTrackingService.getServicesByContract(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContractId()).isEqualTo(1L);
    }

    // ============================================================
    // getServicesByStatus tests
    // ============================================================

    @Test
    void getServicesByStatus_returnsFilteredServices() {
        when(serviceRecordRepository.findByStatus(ServiceStatus.PENDING))
                .thenReturn(List.of(pendingServiceRecord()));

        List<ServiceResponseDTO> result = serviceTrackingService.getServicesByStatus(ServiceStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ServiceStatus.PENDING);
    }

    // ============================================================
    // updateServiceStatus tests
    // ============================================================

    @Test
    void updateServiceStatus_pendingToInProgress_asVendor_succeeds() {
        setVendorInContext();
        ServiceRecord service = pendingServiceRecord(); // status = PENDING
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));
        ServiceRecord saved = pendingServiceRecord();
        saved.setStatus(ServiceStatus.IN_PROGRESS);
        when(serviceRecordRepository.save(service)).thenReturn(saved);

        ServiceResponseDTO result = serviceTrackingService.updateServiceStatus(1L, ServiceStatus.IN_PROGRESS);

        assertThat(result.getStatus()).isEqualTo(ServiceStatus.IN_PROGRESS);
        verify(serviceRecordRepository).save(service);
    }

    @Test
    void updateServiceStatus_inProgressToCompleted_asVendor_succeeds() {
        setVendorInContext();
        ServiceRecord service = pendingServiceRecord();
        service.setStatus(ServiceStatus.IN_PROGRESS);
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));
        ServiceRecord saved = pendingServiceRecord();
        saved.setStatus(ServiceStatus.COMPLETED);
        when(serviceRecordRepository.save(service)).thenReturn(saved);

        ServiceResponseDTO result = serviceTrackingService.updateServiceStatus(1L, ServiceStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(ServiceStatus.COMPLETED);
    }

    @Test
    void updateServiceStatus_completedToVerified_asProjectManager_succeeds() {
        setProjectManagerInContext();
        ServiceRecord service = pendingServiceRecord();
        service.setStatus(ServiceStatus.COMPLETED);
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));
        ServiceRecord saved = pendingServiceRecord();
        saved.setStatus(ServiceStatus.VERIFIED);
        when(serviceRecordRepository.save(service)).thenReturn(saved);

        ServiceResponseDTO result = serviceTrackingService.updateServiceStatus(1L, ServiceStatus.VERIFIED);

        assertThat(result.getStatus()).isEqualTo(ServiceStatus.VERIFIED);
    }

    @Test
    void updateServiceStatus_sameStatus_returnsSameServiceWithoutSaving() {
        ServiceRecord service = pendingServiceRecord(); // status = PENDING
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));

        ServiceResponseDTO result = serviceTrackingService.updateServiceStatus(1L, ServiceStatus.PENDING);

        assertThat(result.getStatus()).isEqualTo(ServiceStatus.PENDING);
        verify(serviceRecordRepository, never()).save(any());
    }

    @Test
    void updateServiceStatus_invalidTransition_throwsBadRequestException() {
        setAdminInContext();
        ServiceRecord service = pendingServiceRecord(); // status = PENDING
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));

        // PENDING → VERIFIED is invalid; must follow PENDING→IN_PROGRESS→COMPLETED→VERIFIED
        assertThatThrownBy(() -> serviceTrackingService.updateServiceStatus(1L, ServiceStatus.VERIFIED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateServiceStatus_serviceNotFound_throwsResourceNotFoundException() {
        when(serviceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTrackingService.updateServiceStatus(99L, ServiceStatus.IN_PROGRESS))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // updateService tests
    // ============================================================

    @Test
    void updateService_whenStatusIsPending_updatesSuccessfully() {
        setAdminInContext();
        ServiceRecord service = pendingServiceRecord();
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));
        when(serviceRecordRepository.save(service)).thenReturn(service);

        ServiceRequestDTO updateReq = new ServiceRequestDTO();
        updateReq.setDescription("Updated plumbing installation work");
        updateReq.setRemarks("Revised scope");

        ServiceResponseDTO result = serviceTrackingService.updateService(1L, updateReq);

        assertThat(result).isNotNull();
        verify(serviceRecordRepository).save(service);
    }

    @Test
    void updateService_whenStatusIsNotPending_throwsBadRequestException() {
        ServiceRecord service = pendingServiceRecord();
        service.setStatus(ServiceStatus.COMPLETED);
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> serviceTrackingService.updateService(1L, serviceRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    // ============================================================
    // deleteService tests
    // ============================================================

    @Test
    void deleteService_whenStatusIsPending_deletesSuccessfully() {
        ServiceRecord service = pendingServiceRecord();
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));

        serviceTrackingService.deleteService(1L);

        verify(serviceRecordRepository).delete(service);
        verify(notificationProducer, atLeastOnce()).send(anyString(), any(NotificationEvent.class));
    }

    @Test
    void deleteService_whenStatusIsNotPending_throwsBadRequestException() {
        ServiceRecord service = pendingServiceRecord();
        service.setStatus(ServiceStatus.VERIFIED);
        when(serviceRecordRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> serviceTrackingService.deleteService(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void deleteService_whenServiceNotFound_throwsResourceNotFoundException() {
        when(serviceRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTrackingService.deleteService(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

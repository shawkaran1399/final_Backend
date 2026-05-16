package com.buildledger.delivery.service.impl;

import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.ContractBudgetSummaryDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.entity.Delivery;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.event.NotificationEvent;
import com.buildledger.delivery.event.NotificationProducer;
import com.buildledger.delivery.exception.BadRequestException;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.exception.ServiceUnavailableException;
import com.buildledger.delivery.feign.ContractServiceClient;
import com.buildledger.delivery.feign.ContractServiceFallback;
import com.buildledger.delivery.feign.VendorServiceClient;
import com.buildledger.delivery.repository.DeliveryRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ServiceRecordRepository serviceRecordRepository;
    @Mock private ContractServiceClient contractServiceClient;
    @Mock private VendorServiceClient vendorServiceClient;
    @Mock private NotificationProducer notificationProducer;

    @InjectMocks private DeliveryServiceImpl deliveryService;

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
        data.put("value", "50000.00");
        return ApiResponseDTO.<Map<String, Object>>builder()
                .success(true).message("Contract retrieved").data(data).build();
    }

    private Delivery pendingDelivery() {
        return Delivery.builder()
                .deliveryId(1L)
                .contractId(1L)
                .date(LocalDate.of(2024, 6, 15))
                .item("Cement Bags")
                .quantity(new BigDecimal("100.00"))
                .unit("bags")
                .remarks("Test remarks")
                .status(DeliveryStatus.PENDING)
                .managerUsername("manager1")
                .vendorUsername("vendor1")
                .build();
    }

    private DeliveryRequestDTO deliveryRequest() {
        DeliveryRequestDTO req = new DeliveryRequestDTO();
        req.setContractId(1L);
        req.setDate(LocalDate.of(2024, 6, 15));
        req.setItem("Cement Bags");
        req.setQuantity(new BigDecimal("100.00"));
        req.setUnit("bags");
        req.setRemarks("Test remarks");
        return req;
    }

    // ============================================================
    // createDelivery tests
    // ============================================================

    @Test
    void createDelivery_withValidData_returnsDeliveryDTO() {
        setAdminInContext();
        when(contractServiceClient.getContractById(1L)).thenReturn(activeContractResponse());
        when(deliveryRepository.save(any(Delivery.class))).thenReturn(pendingDelivery());

        DeliveryResponseDTO result = deliveryService.createDelivery(deliveryRequest());

        assertThat(result).isNotNull();
        assertThat(result.getDeliveryId()).isEqualTo(1L);
        assertThat(result.getItem()).isEqualTo("Cement Bags");
        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        verify(deliveryRepository).save(any(Delivery.class));
        verify(notificationProducer, atLeastOnce()).send(eq("delivery-events"), any(NotificationEvent.class));
    }

    @Test
    void createDelivery_withInactiveContract_throwsBadRequestException() {
        setAdminInContext();
        Map<String, Object> data = new HashMap<>();
        data.put("status", "CLOSED");
        var response = ApiResponseDTO.<Map<String, Object>>builder()
                .success(true).message("ok").data(data).build();
        when(contractServiceClient.getContractById(1L)).thenReturn(response);

        assertThatThrownBy(() -> deliveryService.createDelivery(deliveryRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createDelivery_whenContractServiceUnavailable_throwsServiceUnavailableException() {
        setAdminInContext();
        var fallbackResponse = ApiResponseDTO.<Map<String, Object>>builder()
                .success(false).message(ContractServiceFallback.MARKER).build();
        when(contractServiceClient.getContractById(1L)).thenReturn(fallbackResponse);

        assertThatThrownBy(() -> deliveryService.createDelivery(deliveryRequest()))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void createDelivery_whenContractNotFound_throwsResourceNotFoundException() {
        setAdminInContext();
        var notFound = ApiResponseDTO.<Map<String, Object>>builder()
                .success(false).message("Not found").build();
        when(contractServiceClient.getContractById(1L)).thenReturn(notFound);

        assertThatThrownBy(() -> deliveryService.createDelivery(deliveryRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createDelivery_whenDateOutsideContractPeriod_throwsBadRequestException() {
        setAdminInContext();
        when(contractServiceClient.getContractById(1L)).thenReturn(activeContractResponse());
        DeliveryRequestDTO req = deliveryRequest();
        req.setDate(LocalDate.of(2030, 1, 1)); // outside 2024-01-01 to 2025-12-31

        assertThatThrownBy(() -> deliveryService.createDelivery(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("outside the contract period");
    }

    // ============================================================
    // getDeliveryById tests
    // ============================================================

    @Test
    void getDeliveryById_existingId_returnsDeliveryDTO() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(pendingDelivery()));

        DeliveryResponseDTO result = deliveryService.getDeliveryById(1L);

        assertThat(result.getDeliveryId()).isEqualTo(1L);
        assertThat(result.getItem()).isEqualTo("Cement Bags");
        assertThat(result.getContractId()).isEqualTo(1L);
    }

    @Test
    void getDeliveryById_nonExistingId_throwsResourceNotFoundException() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDeliveryById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // getAllDeliveries tests
    // ============================================================

    @Test
    void getAllDeliveries_returnsAllDeliveries() {
        when(deliveryRepository.findAll()).thenReturn(List.of(pendingDelivery(), pendingDelivery()));

        List<DeliveryResponseDTO> result = deliveryService.getAllDeliveries();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllDeliveries_whenEmpty_returnsEmptyList() {
        when(deliveryRepository.findAll()).thenReturn(List.of());

        List<DeliveryResponseDTO> result = deliveryService.getAllDeliveries();

        assertThat(result).isEmpty();
    }

    // ============================================================
    // getDeliveriesByContract tests
    // ============================================================

    @Test
    void getDeliveriesByContract_returnsDeliveriesForContract() {
        when(deliveryRepository.findByContractId(1L)).thenReturn(List.of(pendingDelivery()));

        List<DeliveryResponseDTO> result = deliveryService.getDeliveriesByContract(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContractId()).isEqualTo(1L);
    }

    // ============================================================
    // updateDeliveryStatus tests
    // ============================================================

    @Test
    void updateDeliveryStatus_pendingToMarkedDelivered_asVendor_succeeds() {
        setVendorInContext();
        Delivery delivery = pendingDelivery(); // status = PENDING
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Delivery saved = pendingDelivery();
        saved.setStatus(DeliveryStatus.MARKED_DELIVERED);
        when(deliveryRepository.save(delivery)).thenReturn(saved);

        DeliveryResponseDTO result = deliveryService.updateDeliveryStatus(1L, DeliveryStatus.MARKED_DELIVERED);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.MARKED_DELIVERED);
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void updateDeliveryStatus_markedDeliveredToAccepted_asProjectManager_succeeds() {
        setProjectManagerInContext();
        Delivery delivery = pendingDelivery();
        delivery.setStatus(DeliveryStatus.MARKED_DELIVERED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Delivery saved = pendingDelivery();
        saved.setStatus(DeliveryStatus.ACCEPTED);
        when(deliveryRepository.save(delivery)).thenReturn(saved);

        DeliveryResponseDTO result = deliveryService.updateDeliveryStatus(1L, DeliveryStatus.ACCEPTED);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
    }

    @Test
    void updateDeliveryStatus_markedDeliveredToRejected_asAdmin_succeeds() {
        setAdminInContext();
        Delivery delivery = pendingDelivery();
        delivery.setStatus(DeliveryStatus.MARKED_DELIVERED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Delivery saved = pendingDelivery();
        saved.setStatus(DeliveryStatus.REJECTED);
        when(deliveryRepository.save(delivery)).thenReturn(saved);

        DeliveryResponseDTO result = deliveryService.updateDeliveryStatus(1L, DeliveryStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.REJECTED);
    }

    @Test
    void updateDeliveryStatus_sameStatus_returnsSameDeliveryWithoutSaving() {
        Delivery delivery = pendingDelivery(); // status = PENDING
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        DeliveryResponseDTO result = deliveryService.updateDeliveryStatus(1L, DeliveryStatus.PENDING);

        assertThat(result.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void updateDeliveryStatus_invalidTransition_throwsBadRequestException() {
        setAdminInContext();
        Delivery delivery = pendingDelivery(); // status = PENDING
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        // PENDING → ACCEPTED is invalid; must pass through MARKED_DELIVERED first
        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(1L, DeliveryStatus.ACCEPTED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateDeliveryStatus_deliveryNotFound_throwsResourceNotFoundException() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(99L, DeliveryStatus.MARKED_DELIVERED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // updateDelivery tests
    // ============================================================

    @Test
    void updateDelivery_whenStatusIsPending_updatesSuccessfully() {
        setAdminInContext();
        Delivery delivery = pendingDelivery();
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(delivery)).thenReturn(delivery);

        DeliveryRequestDTO updateReq = new DeliveryRequestDTO();
        updateReq.setItem("Steel Rods");
        updateReq.setQuantity(new BigDecimal("50.00"));

        DeliveryResponseDTO result = deliveryService.updateDelivery(1L, updateReq);

        assertThat(result).isNotNull();
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void updateDelivery_whenStatusIsNotPending_throwsBadRequestException() {
        Delivery delivery = pendingDelivery();
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.updateDelivery(1L, deliveryRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    // ============================================================
    // deleteDelivery tests
    // ============================================================

    @Test
    void deleteDelivery_whenStatusIsPending_deletesSuccessfully() {
        Delivery delivery = pendingDelivery();
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        deliveryService.deleteDelivery(1L);

        verify(deliveryRepository).delete(delivery);
        verify(notificationProducer, atLeastOnce()).send(anyString(), any(NotificationEvent.class));
    }

    @Test
    void deleteDelivery_whenStatusIsNotPending_throwsBadRequestException() {
        Delivery delivery = pendingDelivery();
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.deleteDelivery(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void deleteDelivery_whenDeliveryNotFound_throwsResourceNotFoundException() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.deleteDelivery(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // getContractBudgetSummary tests
    // ============================================================

    @Test
    void getContractBudgetSummary_returnsCorrectCounts() {
        when(contractServiceClient.getContractById(1L)).thenReturn(activeContractResponse());
        when(deliveryRepository.findByContractId(1L)).thenReturn(List.of(pendingDelivery()));
        when(serviceRecordRepository.findByContractId(1L)).thenReturn(List.of());

        ContractBudgetSummaryDTO result = deliveryService.getContractBudgetSummary(1L);

        assertThat(result.getContractId()).isEqualTo(1L);
        assertThat(result.getContractValue()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(result.getDeliveryCount()).isEqualTo(1);
        assertThat(result.getServiceCount()).isEqualTo(0);
        assertThat(result.isOverBudget()).isFalse();
    }

    @Test
    void getContractBudgetSummary_whenContractNotActive_throwsBadRequestException() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "CLOSED");
        var response = ApiResponseDTO.<Map<String, Object>>builder()
                .success(true).message("ok").data(data).build();
        when(contractServiceClient.getContractById(1L)).thenReturn(response);

        assertThatThrownBy(() -> deliveryService.getContractBudgetSummary(1L))
                .isInstanceOf(BadRequestException.class);
    }
}

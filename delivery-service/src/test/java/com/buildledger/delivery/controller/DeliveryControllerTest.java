package com.buildledger.delivery.controller;

import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.ContractBudgetSummaryDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.security.JwtUtils;
import com.buildledger.delivery.service.DeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeliveryController.class)
@MockBean(JpaMetamodelMappingContext.class)
class DeliveryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DeliveryService deliveryService;
    @MockBean private JwtUtils jwtUtils;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Test Data Helpers ────────────────────────────────────────────────────

    private DeliveryResponseDTO sampleDeliveryResponse() {
        return DeliveryResponseDTO.builder()
                .deliveryId(1L)
                .contractId(1L)
                .date(LocalDate.of(2024, 6, 15))
                .item("Cement Bags")
                .quantity(new BigDecimal("100.00"))
                .unit("bags")
                .remarks("Test remarks")
                .status(DeliveryStatus.PENDING)
                .build();
    }

    private DeliveryRequestDTO sampleDeliveryRequest() {
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
    // POST /deliveries - createDelivery
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void createDelivery_asVendor_returns201WithBody() throws Exception {
        when(deliveryService.createDelivery(any(DeliveryRequestDTO.class)))
                .thenReturn(sampleDeliveryResponse());

        mockMvc.perform(post("/deliveries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDeliveryRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(1))
                .andExpect(jsonPath("$.data.item").value("Cement Bags"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(deliveryService).createDelivery(any(DeliveryRequestDTO.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDelivery_asAdmin_returns201() throws Exception {
        when(deliveryService.createDelivery(any(DeliveryRequestDTO.class)))
                .thenReturn(sampleDeliveryResponse());

        mockMvc.perform(post("/deliveries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDeliveryRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "VENDOR")
    void createDelivery_withMissingContractId_returns400() throws Exception {
        DeliveryRequestDTO invalidRequest = new DeliveryRequestDTO();
        // contractId is null → fails @NotNull validation
        invalidRequest.setDate(LocalDate.of(2024, 6, 15));
        invalidRequest.setItem("Cement Bags");
        invalidRequest.setQuantity(new BigDecimal("100.00"));

        mockMvc.perform(post("/deliveries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(deliveryService, never()).createDelivery(any());
    }

    @Test
    @WithMockUser(roles = "VENDOR")
    void createDelivery_withMissingItem_returns400() throws Exception {
        DeliveryRequestDTO invalidRequest = new DeliveryRequestDTO();
        invalidRequest.setContractId(1L);
        invalidRequest.setDate(LocalDate.of(2024, 6, 15));
        // item is blank → fails @NotBlank validation
        invalidRequest.setQuantity(new BigDecimal("100.00"));

        mockMvc.perform(post("/deliveries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(deliveryService, never()).createDelivery(any());
    }

    // ============================================================
    // GET /deliveries - getAllDeliveries
    // ============================================================

    @Test
    @WithMockUser
    void getAllDeliveries_returns200WithList() throws Exception {
        when(deliveryService.getAllDeliveries())
                .thenReturn(List.of(sampleDeliveryResponse(), sampleDeliveryResponse()));

        mockMvc.perform(get("/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @WithMockUser
    void getAllDeliveries_whenEmpty_returns200WithEmptyList() throws Exception {
        when(deliveryService.getAllDeliveries()).thenReturn(List.of());

        mockMvc.perform(get("/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ============================================================
    // GET /deliveries/{id} - getDeliveryById
    // ============================================================

    @Test
    @WithMockUser
    void getDeliveryById_existingId_returns200WithDelivery() throws Exception {
        when(deliveryService.getDeliveryById(1L)).thenReturn(sampleDeliveryResponse());

        mockMvc.perform(get("/deliveries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(1))
                .andExpect(jsonPath("$.data.item").value("Cement Bags"));
    }

    @Test
    @WithMockUser
    void getDeliveryById_nonExistingId_returns404() throws Exception {
        when(deliveryService.getDeliveryById(99L))
                .thenThrow(new ResourceNotFoundException("Delivery", "id", 99L));

        mockMvc.perform(get("/deliveries/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ============================================================
    // GET /deliveries/contract/{contractId}
    // ============================================================

    @Test
    @WithMockUser
    void getDeliveriesByContract_returns200WithList() throws Exception {
        when(deliveryService.getDeliveriesByContract(1L))
                .thenReturn(List.of(sampleDeliveryResponse()));

        mockMvc.perform(get("/deliveries/contract/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ============================================================
    // GET /deliveries/contract/{contractId}/budget-summary
    // ============================================================

    @Test
    @WithMockUser
    void getContractBudgetSummary_returns200() throws Exception {
        ContractBudgetSummaryDTO summary = ContractBudgetSummaryDTO.builder()
                .contractId(1L)
                .contractValue(new BigDecimal("50000.00"))
                .spent(BigDecimal.ZERO)
                .remaining(new BigDecimal("50000.00"))
                .overBudget(false)
                .deliveryCount(1)
                .serviceCount(0)
                .build();
        when(deliveryService.getContractBudgetSummary(1L)).thenReturn(summary);

        mockMvc.perform(get("/deliveries/contract/1/budget-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contractId").value(1))
                .andExpect(jsonPath("$.data.overBudget").value(false));
    }

    // ============================================================
    // PATCH /deliveries/{id}/status - updateDeliveryStatus
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void updateDeliveryStatus_returns200() throws Exception {
        DeliveryResponseDTO updated = sampleDeliveryResponse();
        updated.setStatus(DeliveryStatus.MARKED_DELIVERED);
        when(deliveryService.updateDeliveryStatus(eq(1L), eq(DeliveryStatus.MARKED_DELIVERED)))
                .thenReturn(updated);

        mockMvc.perform(patch("/deliveries/1/status")
                        .with(csrf())
                        .param("status", "MARKED_DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("MARKED_DELIVERED"));
    }

    // ============================================================
    // PUT /deliveries/{id} - updateDelivery
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void updateDelivery_asVendor_returns200() throws Exception {
        DeliveryResponseDTO updated = sampleDeliveryResponse();
        updated.setItem("Steel Rods");
        when(deliveryService.updateDelivery(eq(1L), any(DeliveryRequestDTO.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/deliveries/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleDeliveryRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ============================================================
    // DELETE /deliveries/{id} - deleteDelivery
    // ============================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDelivery_asAdmin_returns200() throws Exception {
        doNothing().when(deliveryService).deleteDelivery(1L);

        mockMvc.perform(delete("/deliveries/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery deleted successfully"));

        verify(deliveryService).deleteDelivery(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDelivery_whenNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Delivery", "id", 99L))
                .when(deliveryService).deleteDelivery(99L);

        mockMvc.perform(delete("/deliveries/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

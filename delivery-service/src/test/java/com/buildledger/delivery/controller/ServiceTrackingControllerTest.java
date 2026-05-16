package com.buildledger.delivery.controller;

import com.buildledger.delivery.dto.request.ServiceRequestDTO;
import com.buildledger.delivery.dto.response.ServiceResponseDTO;
import com.buildledger.delivery.enums.ServiceStatus;
import com.buildledger.delivery.exception.ResourceNotFoundException;
import com.buildledger.delivery.security.JwtUtils;
import com.buildledger.delivery.service.ServiceTrackingService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServiceTrackingController.class)
@MockBean(JpaMetamodelMappingContext.class)
class ServiceTrackingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ServiceTrackingService serviceTrackingService;
    @MockBean private JwtUtils jwtUtils;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Test Data Helpers ────────────────────────────────────────────────────

    private ServiceResponseDTO sampleServiceResponse() {
        return ServiceResponseDTO.builder()
                .serviceId(1L)
                .contractId(1L)
                .description("Install electrical wiring and fixtures")
                .completionDate(LocalDate.of(2024, 8, 30))
                .status(ServiceStatus.PENDING)
                .remarks("Standard installation")
                .build();
    }

    private ServiceRequestDTO sampleServiceRequest() {
        ServiceRequestDTO req = new ServiceRequestDTO();
        req.setContractId(1L);
        req.setDescription("Install electrical wiring and fixtures");
        req.setCompletionDate(LocalDate.of(2024, 8, 30));
        req.setRemarks("Standard installation");
        return req;
    }

    // ============================================================
    // POST /services - createService
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void createService_asVendor_returns201() throws Exception {
        when(serviceTrackingService.createService(any(ServiceRequestDTO.class)))
                .thenReturn(sampleServiceResponse());

        mockMvc.perform(post("/services")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleServiceRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(serviceTrackingService).createService(any(ServiceRequestDTO.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createService_asAdmin_returns201() throws Exception {
        when(serviceTrackingService.createService(any(ServiceRequestDTO.class)))
                .thenReturn(sampleServiceResponse());

        mockMvc.perform(post("/services")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleServiceRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "VENDOR")
    void createService_withMissingDescription_returns400() throws Exception {
        ServiceRequestDTO invalidRequest = new ServiceRequestDTO();
        invalidRequest.setContractId(1L);
        // description is blank → fails @NotBlank validation
        invalidRequest.setCompletionDate(LocalDate.of(2024, 8, 30));

        mockMvc.perform(post("/services")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(serviceTrackingService, never()).createService(any());
    }

    // ============================================================
    // GET /services - getAllServices
    // ============================================================

    @Test
    @WithMockUser
    void getAllServices_returns200WithList() throws Exception {
        when(serviceTrackingService.getAllServices())
                .thenReturn(List.of(sampleServiceResponse(), sampleServiceResponse()));

        mockMvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ============================================================
    // GET /services/{id} - getServiceById
    // ============================================================

    @Test
    @WithMockUser
    void getServiceById_existingId_returns200() throws Exception {
        when(serviceTrackingService.getServiceById(1L)).thenReturn(sampleServiceResponse());

        mockMvc.perform(get("/services/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceId").value(1))
                .andExpect(jsonPath("$.data.description").value("Install electrical wiring and fixtures"));
    }

    @Test
    @WithMockUser
    void getServiceById_nonExistingId_returns404() throws Exception {
        when(serviceTrackingService.getServiceById(99L))
                .thenThrow(new ResourceNotFoundException("Service", "id", 99L));

        mockMvc.perform(get("/services/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ============================================================
    // GET /services/contract/{contractId}
    // ============================================================

    @Test
    @WithMockUser
    void getServicesByContract_returns200() throws Exception {
        when(serviceTrackingService.getServicesByContract(1L))
                .thenReturn(List.of(sampleServiceResponse()));

        mockMvc.perform(get("/services/contract/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ============================================================
    // GET /services/status/{status}
    // ============================================================

    @Test
    @WithMockUser
    void getServicesByStatus_returns200() throws Exception {
        when(serviceTrackingService.getServicesByStatus(ServiceStatus.PENDING))
                .thenReturn(List.of(sampleServiceResponse()));

        mockMvc.perform(get("/services/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ============================================================
    // PATCH /services/{id}/status - updateServiceStatus
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void updateServiceStatus_returns200() throws Exception {
        ServiceResponseDTO updated = sampleServiceResponse();
        updated.setStatus(ServiceStatus.IN_PROGRESS);
        when(serviceTrackingService.updateServiceStatus(eq(1L), eq(ServiceStatus.IN_PROGRESS)))
                .thenReturn(updated);

        mockMvc.perform(patch("/services/1/status")
                        .with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    // ============================================================
    // PUT /services/{id} - updateService
    // ============================================================

    @Test
    @WithMockUser(roles = "VENDOR")
    void updateService_asVendor_returns200() throws Exception {
        when(serviceTrackingService.updateService(eq(1L), any(ServiceRequestDTO.class)))
                .thenReturn(sampleServiceResponse());

        mockMvc.perform(put("/services/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleServiceRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ============================================================
    // DELETE /services/{id} - deleteService
    // ============================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteService_asAdmin_returns200() throws Exception {
        doNothing().when(serviceTrackingService).deleteService(1L);

        mockMvc.perform(delete("/services/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Service record deleted successfully"));

        verify(serviceTrackingService).deleteService(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteService_whenNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Service", "id", 99L))
                .when(serviceTrackingService).deleteService(99L);

        mockMvc.perform(delete("/services/99").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

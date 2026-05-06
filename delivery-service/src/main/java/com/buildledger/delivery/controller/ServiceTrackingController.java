package com.buildledger.delivery.controller;

import com.buildledger.delivery.dto.request.ServiceRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.ServiceResponseDTO;
import com.buildledger.delivery.enums.ServiceStatus;
import com.buildledger.delivery.service.ServiceTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
@Tag(name = "Service Tracking")
@SecurityRequirement(name = "bearerAuth")
public class ServiceTrackingController {

    private final ServiceTrackingService serviceTrackingService;

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    @Operation(summary = "Create service record [VENDOR / ADMIN]")
    public ResponseEntity<ApiResponseDTO<ServiceResponseDTO>> createService(
            @Valid @RequestBody ServiceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Service record created", serviceTrackingService.createService(request)));
    }

    @GetMapping
    @Operation(summary = "Get all services [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ServiceResponseDTO>>> getAllServices() {
        return ResponseEntity.ok(ApiResponseDTO.success("Services retrieved", serviceTrackingService.getAllServices()));
    }

    @GetMapping("/{serviceId}")
    @Operation(summary = "Get service by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<ServiceResponseDTO>> getServiceById(@PathVariable Long serviceId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Service retrieved",
            serviceTrackingService.getServiceById(serviceId)));
    }

    @GetMapping("/contract/{contractId}")
    @Operation(summary = "Get services by contract [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ServiceResponseDTO>>> getServicesByContract(
            @PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Services retrieved",
            serviceTrackingService.getServicesByContract(contractId)));
    }

    @PatchMapping("/{serviceId}/status")
    @Operation(summary = "Update service status",
               description = "Lifecycle: PENDING→IN_PROGRESS→COMPLETED→VERIFIED. " +
                             "IN_PROGRESS/COMPLETED require VENDOR or ADMIN. VERIFIED requires PROJECT_MANAGER or ADMIN.")
    public ResponseEntity<ApiResponseDTO<ServiceResponseDTO>> updateServiceStatus(
            @PathVariable Long serviceId,
            @RequestParam ServiceStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Service status updated",
            serviceTrackingService.updateServiceStatus(serviceId, status)));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    @Operation(summary = "Update service record [VENDOR / ADMIN] – only when PENDING")
    public ResponseEntity<ApiResponseDTO<ServiceResponseDTO>> updateService(
            @PathVariable Long serviceId,
            @Valid @RequestBody ServiceRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Service updated",
            serviceTrackingService.updateService(serviceId, request)));
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete service record [ADMIN only] – only when PENDING")
    public ResponseEntity<ApiResponseDTO<Void>> deleteService(@PathVariable Long serviceId) {
        serviceTrackingService.deleteService(serviceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Service record deleted successfully"));
    }
}


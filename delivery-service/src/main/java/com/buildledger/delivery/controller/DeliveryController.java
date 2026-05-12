package com.buildledger.delivery.controller;

import com.buildledger.delivery.dto.request.DeliveryRequestDTO;
import com.buildledger.delivery.dto.response.ApiResponseDTO;
import com.buildledger.delivery.dto.response.ContractBudgetSummaryDTO;
import com.buildledger.delivery.dto.response.DeliveryResponseDTO;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.service.DeliveryService;
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
@RequestMapping("/deliveries")
@RequiredArgsConstructor
@Tag(name = "Delivery Management")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    @Operation(summary = "Create delivery [VENDOR / ADMIN]")
    public ResponseEntity<ApiResponseDTO<DeliveryResponseDTO>> createDelivery(
            @Valid @RequestBody DeliveryRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Delivery created successfully",
                        deliveryService.createDelivery(request)));
    }

    @GetMapping
    @Operation(summary = "Get all deliveries [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<DeliveryResponseDTO>>> getAllDeliveries() {
        return ResponseEntity.ok(ApiResponseDTO.success("Deliveries retrieved",
                deliveryService.getAllDeliveries()));
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get delivery by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<DeliveryResponseDTO>> getDeliveryById(
            @PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Delivery retrieved",
                deliveryService.getDeliveryById(deliveryId)));
    }

    @GetMapping("/contract/{contractId}")
    @Operation(summary = "Get deliveries by contract [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<DeliveryResponseDTO>>> getDeliveriesByContract(
            @PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Deliveries retrieved",
                deliveryService.getDeliveriesByContract(contractId)));
    }

    /**
     * Contract-level budget summary for delivery/service price validation.
     * remaining = contractValue - sum(delivery prices) - sum(service prices)
     * Frontend uses this to show live budget breakdown and hard-block if exceeded.
     *
     * GET /deliveries/contract/{contractId}/budget-summary
     */
    @GetMapping("/contract/{contractId}/budget-summary")
    @Operation(summary = "Get contract budget summary [ALL roles]",
            description = "Returns contractValue, spent (sum of delivery+service prices), remaining, overBudget")
    public ResponseEntity<ApiResponseDTO<ContractBudgetSummaryDTO>> getContractBudgetSummary(
            @PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract budget summary retrieved",
                deliveryService.getContractBudgetSummary(contractId)));
    }

    @PatchMapping("/{deliveryId}/status")
    @Operation(summary = "Update delivery status",
            description = "PENDING→MARKED_DELIVERED|DELAYED, MARKED_DELIVERED→ACCEPTED|REJECTED, DELAYED→MARKED_DELIVERED. " +
                    "ACCEPTED/REJECTED require PROJECT_MANAGER or ADMIN. MARKED_DELIVERED/DELAYED require VENDOR or ADMIN.")
    public ResponseEntity<ApiResponseDTO<DeliveryResponseDTO>> updateDeliveryStatus(
            @PathVariable Long deliveryId,
            @RequestParam DeliveryStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Delivery status updated",
                deliveryService.updateDeliveryStatus(deliveryId, status)));
    }

    @PutMapping("/{deliveryId}")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    @Operation(summary = "Update delivery details [VENDOR / ADMIN] – only when PENDING")
    public ResponseEntity<ApiResponseDTO<DeliveryResponseDTO>> updateDelivery(
            @PathVariable Long deliveryId,
            @Valid @RequestBody DeliveryRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Delivery updated",
                deliveryService.updateDelivery(deliveryId, request)));
    }

    @DeleteMapping("/{deliveryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete delivery [ADMIN only] – only when PENDING")
    public ResponseEntity<ApiResponseDTO<Void>> deleteDelivery(@PathVariable Long deliveryId) {
        deliveryService.deleteDelivery(deliveryId);
        return ResponseEntity.ok(ApiResponseDTO.success("Delivery deleted successfully"));
    }
}
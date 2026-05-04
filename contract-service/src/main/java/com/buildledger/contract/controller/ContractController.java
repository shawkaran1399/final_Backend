package com.buildledger.contract.controller;

import com.buildledger.contract.dto.request.ContractRequestDTO;
import com.buildledger.contract.dto.request.ContractTermRequestDTO;
import com.buildledger.contract.dto.response.*;
import com.buildledger.contract.enums.ContractStatus;
import com.buildledger.contract.service.ContractService;
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
@RequestMapping("/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract Management")
@SecurityRequirement(name = "bearerAuth")
public class ContractController {

    private final ContractService contractService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Create contract [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> createContract(
            @Valid @RequestBody ContractRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Contract created successfully", contractService.createContract(request)));
    }

    @GetMapping
    @Operation(summary = "Get all contracts [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getAllContracts() {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getAllContracts()));
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Get contract by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> getContractById(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract retrieved", contractService.getContractById(contractId)));
    }

    @GetMapping("/vendor/{vendorId}")
    @Operation(summary = "Get contracts by vendor [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getContractsByVendor(@PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getContractsByVendor(vendorId)));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get contracts by project [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getContractsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getContractsByProject(projectId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get contracts by status [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getContractsByStatus(@PathVariable ContractStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getContractsByStatus(status)));
    }

    @PutMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update contract [ADMIN / PROJECT_MANAGER] – only in DRAFT status")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> updateContract(
            @PathVariable Long contractId, @Valid @RequestBody ContractRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract updated", contractService.updateContract(contractId, request)));
    }

    @PatchMapping("/{contractId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update contract status [ADMIN / PROJECT_MANAGER]",
               description = "Lifecycle: DRAFT→ACTIVE, ACTIVE→COMPLETED|TERMINATED|EXPIRED")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> updateContractStatus(
            @PathVariable Long contractId, @RequestParam ContractStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract status updated",
            contractService.updateContractStatus(contractId, status)));
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Delete contract [ADMIN / PROJECT_MANAGER] – only in DRAFT status")
    public ResponseEntity<ApiResponseDTO<Void>> deleteContract(@PathVariable Long contractId) {
        contractService.deleteContract(contractId);
        return ResponseEntity.ok(ApiResponseDTO.success("Contract deleted successfully"));
    }

    @PostMapping("/{contractId}/terms")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Add contract term [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<ApiResponseDTO<ContractTermResponseDTO>> addContractTerm(
            @PathVariable Long contractId, @Valid @RequestBody ContractTermRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Contract term added", contractService.addContractTerm(contractId, request)));
    }

    @GetMapping("/{contractId}/terms")
    @Operation(summary = "Get contract terms [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractTermResponseDTO>>> getContractTerms(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract terms retrieved", contractService.getContractTerms(contractId)));
    }

    @PutMapping("/terms/{termId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Edit contract term [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<ApiResponseDTO<ContractTermResponseDTO>> editContractTerm(
            @PathVariable Long termId, @Valid @RequestBody ContractTermRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract term updated", contractService.editContractTerm(termId, request)));
    }

    @DeleteMapping("/terms/{termId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Delete contract term [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<ApiResponseDTO<Void>> deleteContractTerm(@PathVariable Long termId) {
        contractService.deleteContractTerm(termId);
        return ResponseEntity.ok(ApiResponseDTO.success("Contract term deleted"));
    }

    @PatchMapping("/internal/vendor/{vendorId}/name")
    @Operation(summary = "Internal: propagate vendor name change to all contracts [INTERNAL]")
    public ResponseEntity<ApiResponseDTO<Void>> propagateVendorName(
            @PathVariable Long vendorId,
            @RequestParam String name) {
        contractService.propagateVendorNameChange(vendorId, name);
        return ResponseEntity.ok(ApiResponseDTO.success("Vendor name propagated"));
    }
}


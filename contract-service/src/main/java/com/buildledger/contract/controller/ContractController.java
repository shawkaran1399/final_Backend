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
import org.springframework.security.core.Authentication;
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
    @Operation(summary = "Get all contracts [ADMIN only]")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getAllContracts() {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getAllContracts()));
    }

    /**
     * PROJECT_MANAGER — sees only contracts belonging to their assigned projects.
     * Uses managerUsername from JWT to find their projects, then returns those contracts.
     */
    @GetMapping("/manager/my")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Get contracts for the logged-in PM's projects [PROJECT_MANAGER only]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getMyContracts(
            Authentication authentication) {
        String managerUsername = authentication.getName();
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved",
                contractService.getContractsByManagerUsername(managerUsername)));
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

    @GetMapping("/project/{projectId}/budget-summary")
    @Operation(summary = "Get budget summary for a project [ALL roles]")
    public ResponseEntity<ApiResponseDTO<BudgetSummaryDTO>> getProjectBudgetSummary(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Budget summary retrieved",
                contractService.getProjectBudgetSummary(projectId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get contracts by status [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ContractResponseDTO>>> getContractsByStatus(@PathVariable ContractStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contracts retrieved", contractService.getContractsByStatus(status)));
    }

    @PutMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update contract [ADMIN / PROJECT_MANAGER] – DRAFT only")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> updateContract(
            @PathVariable Long contractId, @Valid @RequestBody ContractRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract updated", contractService.updateContract(contractId, request)));
    }

    @PatchMapping("/{contractId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update contract status [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> updateContractStatus(
            @PathVariable Long contractId, @RequestParam ContractStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Contract status updated",
                contractService.updateContractStatus(contractId, status)));
    }

    @PatchMapping("/{contractId}/respond")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Vendor accept or reject a PENDING contract [VENDOR]")
    public ResponseEntity<ApiResponseDTO<ContractResponseDTO>> vendorRespond(
            @PathVariable Long contractId,
            @RequestParam String action,
            @RequestParam(required = false) String remarks,
            @RequestParam Long vendorId) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "ACCEPT".equalsIgnoreCase(action) ? "Contract accepted" : "Contract rejected",
                contractService.vendorRespondToContract(contractId, action, remarks, vendorId)));
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Delete contract [ADMIN / PROJECT_MANAGER] – DRAFT only")
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
}
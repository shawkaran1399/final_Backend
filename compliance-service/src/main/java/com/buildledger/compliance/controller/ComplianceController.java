package com.buildledger.compliance.controller;

import com.buildledger.compliance.dto.request.ComplianceRecordRequestDTO;
import com.buildledger.compliance.dto.response.ApiResponseDTO;
import com.buildledger.compliance.dto.response.ComplianceRecordResponseDTO;
import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import com.buildledger.compliance.service.ComplianceService;
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
@RequestMapping("/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance Management")
@SecurityRequirement(name = "bearerAuth")
public class ComplianceController {

    private final ComplianceService complianceService;

    @PostMapping
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Create compliance record [COMPLIANCE_OFFICER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<ComplianceRecordResponseDTO>> create(
            @Valid @RequestBody ComplianceRecordRequestDTO request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Compliance record created",
                complianceService.createComplianceRecord(request, username)));
    }

    @GetMapping
    @Operation(summary = "Get all compliance records [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ComplianceRecordResponseDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponseDTO.success("Records retrieved",
            complianceService.getAllComplianceRecords()));
    }

    @GetMapping("/{complianceId}")
    @Operation(summary = "Get compliance record by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<ComplianceRecordResponseDTO>> getById(@PathVariable Long complianceId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Record retrieved",
            complianceService.getComplianceRecordById(complianceId)));
    }

    @GetMapping("/contract/{contractId}")
    @Operation(summary = "Get compliance records by contract [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ComplianceRecordResponseDTO>>> getByContract(
            @PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Records retrieved",
            complianceService.getByContract(contractId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get compliance records by status [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ComplianceRecordResponseDTO>>> getByStatus(
            @PathVariable ComplianceStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Records retrieved",
            complianceService.getByStatus(status)));
    }

    @PutMapping("/{complianceId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Update compliance record [COMPLIANCE_OFFICER / ADMIN]",
               description = "Only allowed when record is in PENDING status")
    public ResponseEntity<ApiResponseDTO<ComplianceRecordResponseDTO>> update(
            @PathVariable Long complianceId,
            @Valid @RequestBody ComplianceRecordRequestDTO request,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(ApiResponseDTO.success("Compliance record updated",
            complianceService.updateComplianceRecord(complianceId, request, username)));
    }

    @PatchMapping("/{complianceId}/status")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Update compliance record status [COMPLIANCE_OFFICER / ADMIN]",
               description = "Lifecycle: PENDING→UNDER_REVIEW, UNDER_REVIEW→PASSED|FAILED|WAIVED, FAILED→PENDING. " +
                             "remarks is REQUIRED when setting status=FAILED.")
    public ResponseEntity<ApiResponseDTO<ComplianceRecordResponseDTO>> updateStatus(
            @PathVariable Long complianceId,
            @RequestParam ComplianceStatus status,
            @RequestParam(required = false) String remarks,
            @RequestHeader(value = "X-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(ApiResponseDTO.success("Compliance status updated",
            complianceService.updateComplianceStatus(complianceId, status, username, remarks)));
    }

    @DeleteMapping("/{complianceId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Delete compliance record [COMPLIANCE_OFFICER / ADMIN]",
               description = "Only PENDING compliance records can be deleted")
    public ResponseEntity<ApiResponseDTO<Void>> delete(@PathVariable Long complianceId) {
        complianceService.deleteComplianceRecord(complianceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Compliance record deleted successfully"));
    }

    @GetMapping("/check/{referenceId}")
    @Operation(summary = "Check if compliance has PASSED or WAIVED for a reference entity [ALL roles]",
               description = "Used by other services to gate approvals. type = DELIVERY_CHECK | INVOICE_CHECK | SERVICE_CHECK | DOCUMENT_CHECK")
    public ResponseEntity<ApiResponseDTO<Boolean>> checkCompliance(
            @PathVariable Long referenceId,
            @RequestParam ComplianceType type) {
        return ResponseEntity.ok(ApiResponseDTO.success("Compliance check result",
            complianceService.isCompliancePassed(referenceId, type)));
    }
}

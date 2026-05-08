package com.buildledger.compliance.controller;

import com.buildledger.compliance.dto.request.AuditRequestDTO;
import com.buildledger.compliance.dto.response.ApiResponseDTO;
import com.buildledger.compliance.dto.response.AuditResponseDTO;
import com.buildledger.compliance.dto.response.ComplianceAuditLogResponseDTO;
import com.buildledger.compliance.enums.AuditStatus;
import com.buildledger.compliance.service.AuditService;
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
@RequestMapping("/audits")
@RequiredArgsConstructor
@Tag(name = "Audit Management")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Create audit [COMPLIANCE_OFFICER / ADMIN]",
               description = "Validates that the complianceOfficerId exists in IAM and matches the authenticated user (ADMIN may assign to others)")
    public ResponseEntity<ApiResponseDTO<AuditResponseDTO>> createAudit(
            @Valid @RequestBody AuditRequestDTO request,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-Role") String requestUserRole) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Audit scheduled",
                auditService.createAudit(request, requestUserId, requestUserRole)));
    }

    @GetMapping
    @Operation(summary = "Get all audits [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<AuditResponseDTO>>> getAllAudits() {
        return ResponseEntity.ok(ApiResponseDTO.success("Audits retrieved", auditService.getAllAudits()));
    }

    @GetMapping("/{auditId}")
    @Operation(summary = "Get audit by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<AuditResponseDTO>> getAuditById(@PathVariable Long auditId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Audit retrieved", auditService.getAuditById(auditId)));
    }

    @GetMapping("/officer/{officerId}")
    @Operation(summary = "Get audits by compliance officer [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<AuditResponseDTO>>> getByOfficer(@PathVariable Long officerId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Audits retrieved",
            auditService.getAuditsByOfficer(officerId)));
    }

    @PutMapping("/{auditId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Update audit details [COMPLIANCE_OFFICER / ADMIN]",
               description = "Only allowed when audit is in SCHEDULED status")
    public ResponseEntity<ApiResponseDTO<AuditResponseDTO>> updateAudit(
            @PathVariable Long auditId,
            @Valid @RequestBody AuditRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Audit updated",
            auditService.updateAudit(auditId, request)));
    }

    @PatchMapping("/{auditId}/status")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Update audit status [COMPLIANCE_OFFICER / ADMIN]",
               description = "Lifecycle: SCHEDULED→IN_PROGRESS|CANCELLED, IN_PROGRESS→PENDING_REVIEW|CANCELLED, PENDING_REVIEW→COMPLETED|CANCELLED")
    public ResponseEntity<ApiResponseDTO<AuditResponseDTO>> updateAuditStatus(
            @PathVariable Long auditId,
            @RequestParam AuditStatus status,
            @RequestParam(required = false) String findings) {
        return ResponseEntity.ok(ApiResponseDTO.success("Audit status updated",
            auditService.updateAuditStatus(auditId, status, findings)));
    }

    @DeleteMapping("/{auditId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Delete audit [COMPLIANCE_OFFICER / ADMIN]",
               description = "Only SCHEDULED or CANCELLED audits can be deleted")
    public ResponseEntity<ApiResponseDTO<Void>> deleteAudit(@PathVariable Long auditId) {
        auditService.deleteAudit(auditId);
        return ResponseEntity.ok(ApiResponseDTO.success("Audit deleted successfully"));
    }

    // ── Compliance Audit Log endpoints (read-only) ────────────────────────

    @GetMapping("/compliance/{complianceRecordId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Get audit log for a compliance record [COMPLIANCE_OFFICER / AUDITOR / ADMIN]",
               description = "Returns all status-change history entries for a given compliance record, ordered by time")
    public ResponseEntity<ApiResponseDTO<List<ComplianceAuditLogResponseDTO>>> getByComplianceRecord(
            @PathVariable Long complianceRecordId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Compliance audit log retrieved",
            auditService.getAuditsByComplianceRecord(complianceRecordId)));
    }

    @GetMapping("/logs/{logId}")
    @PreAuthorize("hasRole('COMPLIANCE_OFFICER') or hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Get single audit log entry by ID [COMPLIANCE_OFFICER / AUDITOR / ADMIN]")
    public ResponseEntity<ApiResponseDTO<ComplianceAuditLogResponseDTO>> getAuditLogById(
            @PathVariable Long logId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Audit log entry retrieved",
            auditService.getAuditLogById(logId)));
    }
}

package com.buildledger.compliance.service;

import com.buildledger.compliance.dto.request.AuditRequestDTO;
import com.buildledger.compliance.dto.response.AuditResponseDTO;
import com.buildledger.compliance.dto.response.ComplianceAuditLogResponseDTO;
import com.buildledger.compliance.enums.AuditStatus;
import java.util.List;

public interface AuditService {
    // ── Scheduled audits (manual) ──────────────────────────────────────────
    AuditResponseDTO createAudit(AuditRequestDTO request, Long requestUserId, String requestUserRole);
    AuditResponseDTO getAuditById(Long auditId);
    List<AuditResponseDTO> getAllAudits();
    List<AuditResponseDTO> getAuditsByOfficer(Long officerId);
    AuditResponseDTO updateAudit(Long auditId, AuditRequestDTO request);
    AuditResponseDTO updateAuditStatus(Long auditId, AuditStatus newStatus, String findings);
    void deleteAudit(Long auditId);

    // ── Compliance audit log (automatic, immutable) ────────────────────────
    void createAuditEntry(Long complianceRecordId, String action, String performedBy, String findings);
    List<ComplianceAuditLogResponseDTO> getAuditsByComplianceRecord(Long complianceRecordId);
    ComplianceAuditLogResponseDTO getAuditLogById(Long logId);
}

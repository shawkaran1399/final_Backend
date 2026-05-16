package com.buildledger.compliance.service.impl;
import com.buildledger.compliance.event.NotificationEvent;
import com.buildledger.compliance.event.NotificationProducer;
import com.buildledger.compliance.dto.request.AuditRequestDTO;
import com.buildledger.compliance.dto.response.*;
import com.buildledger.compliance.entity.Audit;
import com.buildledger.compliance.entity.ComplianceAuditLog;
import com.buildledger.compliance.enums.AuditStatus;
import com.buildledger.compliance.exception.BadRequestException;
import com.buildledger.compliance.exception.ResourceNotFoundException;
import com.buildledger.compliance.exception.ServiceUnavailableException;
import com.buildledger.compliance.feign.IamServiceClient;
import com.buildledger.compliance.feign.IamServiceFallback;
import com.buildledger.compliance.repository.AuditRepository;
import com.buildledger.compliance.repository.ComplianceAuditLogRepository;
import com.buildledger.compliance.service.AuditService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;
    private final ComplianceAuditLogRepository auditLogRepository;
    private final IamServiceClient iamServiceClient;
    private final NotificationProducer notificationProducer;

    @Override
    public AuditResponseDTO createAudit(AuditRequestDTO request, Long requestUserId, String requestUserRole) {
        log.info("Creating audit for officer {}", request.getComplianceOfficerId());
        if (!request.getComplianceOfficerId().equals(requestUserId) && !"ADMIN".equals(requestUserRole)) {
            throw new BadRequestException(
                    "Officer ID in request (" + request.getComplianceOfficerId() + ") does not match " +
                            "the authenticated user (" + requestUserId + "). Only ADMIN can create audits on behalf of others.");
        }
        Map<String, Object> officer = validateComplianceOfficer(request.getComplianceOfficerId());

        Audit audit = Audit.builder()
                .complianceOfficerId(request.getComplianceOfficerId())
                .officerName((String) officer.get("name"))
                .scope(request.getScope())
                .findings(request.getFindings())
                .date(request.getDate())
                .status(AuditStatus.IN_PROGRESS)
                .build();

        AuditResponseDTO result = mapToResponse(auditRepository.save(audit));

        String officerUsername = (String) officer.getOrDefault("username", "");
        sendAuditNotif("AUDIT_SCHEDULED",
                "New audit assigned to you",
                "Dear " + audit.getOfficerName() + ", a new audit has been scheduled for you. Scope: "
                        + audit.getScope() + ". Scheduled date: " + audit.getDate(),
                String.valueOf(result.getAuditId()),
                officerUsername, ADMIN_USERNAME);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditResponseDTO getAuditById(Long auditId) {
        return mapToResponse(findById(auditId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditResponseDTO> getAllAudits() {
        return auditRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditResponseDTO> getAuditsByOfficer(Long officerId) {
        return auditRepository.findByComplianceOfficerId(officerId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public AuditResponseDTO updateAudit(Long auditId, AuditRequestDTO request) {
        Audit audit = findById(auditId);

        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new BadRequestException(
                    "Audit can only be updated in IN_PROGRESS status. Current status: " + audit.getStatus());
        }

        if (request.getComplianceOfficerId() != null &&
                !request.getComplianceOfficerId().equals(audit.getComplianceOfficerId())) {
            Map<String, Object> officer = validateComplianceOfficer(request.getComplianceOfficerId());
            audit.setComplianceOfficerId(request.getComplianceOfficerId());
            audit.setOfficerName((String) officer.get("name"));
        }
        if (request.getScope() != null) audit.setScope(request.getScope());
        if (request.getFindings() != null) audit.setFindings(request.getFindings());
        if (request.getDate() != null) audit.setDate(request.getDate());

        return mapToResponse(auditRepository.save(audit));
    }

    @Override
    public AuditResponseDTO updateAuditStatus(Long auditId, AuditStatus newStatus, String findings) {
        Audit audit = findById(auditId);
        AuditStatus current = audit.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException(
                    "Invalid audit status transition from " + current + " to " + newStatus +
                            ". Lifecycle: IN_PROGRESS→PENDING_REVIEW|CANCELLED, PENDING_REVIEW→COMPLETED|CANCELLED.");
        }

        if (newStatus == AuditStatus.COMPLETED) {
            String effectiveFindings = (findings != null && !findings.isBlank()) ? findings : audit.getFindings();
            if (effectiveFindings == null || effectiveFindings.isBlank()) {
                throw new BadRequestException(
                        "Findings must be recorded before marking an audit as COMPLETED.");
            }
        }

        audit.setStatus(newStatus);
        if (newStatus == AuditStatus.IN_PROGRESS && audit.getAuditDate() == null) {
            audit.setAuditDate(LocalDate.now());
        }
        if (findings != null && !findings.isBlank()) {
            audit.setFindings(findings);
        }

        AuditResponseDTO statusResult = mapToResponse(auditRepository.save(audit));

        String auditType, auditSubject, auditMessage;
        switch (newStatus) {
            case PENDING_REVIEW -> {
                auditType = "AUDIT_PENDING_REVIEW";
                auditSubject = "Audit #" + auditId + " submitted for review";
                auditMessage = "Audit #" + auditId + " with scope '" + audit.getScope()
                        + "' has been submitted for PENDING REVIEW.";
            }
            case COMPLETED -> {
                auditType = "AUDIT_COMPLETED";
                auditSubject = "Audit #" + auditId + " has been completed";
                auditMessage = "Audit #" + auditId + " with scope '" + audit.getScope()
                        + "' has been COMPLETED. Findings: " + audit.getFindings();
            }
            case CANCELLED -> {
                auditType = "AUDIT_CANCELLED";
                auditSubject = "Audit #" + auditId + " has been cancelled";
                auditMessage = "Audit #" + auditId + " with scope '" + audit.getScope()
                        + "' has been CANCELLED.";
            }
            default -> {
                auditType = null;
                auditSubject = null;
                auditMessage = null;
            }
        }

        if (auditType != null) {
            sendAuditNotif(auditType, auditSubject, auditMessage,
                    String.valueOf(auditId),
                    audit.getOfficerUsername(), ADMIN_USERNAME);
        }
        return statusResult;
    }

    @Override
    public void deleteAudit(Long auditId) {
        Audit audit = findById(auditId);
        if (audit.getStatus() != AuditStatus.IN_PROGRESS && audit.getStatus() != AuditStatus.CANCELLED) {
            throw new BadRequestException(
                    "Only IN_PROGRESS or CANCELLED audits can be deleted. Current status: " + audit.getStatus());
        }
        auditRepository.delete(audit);
        log.info("Audit deleted: id={}", auditId);
    }

    // ── Compliance Audit Log (automatic, immutable) ───────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAuditEntry(Long complianceRecordId, String action, String performedBy, String findings) {
        ComplianceAuditLog entry = ComplianceAuditLog.builder()
                .complianceRecordId(complianceRecordId)
                .action(action)
                .performedBy(performedBy)
                .findings(findings)
                .build();
        auditLogRepository.save(entry);
        log.info("Audit log created: compliance #{} → {} by {}", complianceRecordId, action, performedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceAuditLogResponseDTO> getAuditsByComplianceRecord(Long complianceRecordId) {
        return auditLogRepository.findByComplianceRecordIdOrderByCreatedAtAsc(complianceRecordId)
                .stream().map(this::mapLogToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceAuditLogResponseDTO getAuditLogById(Long logId) {
        ComplianceAuditLog entry = auditLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", logId));
        return mapLogToResponse(entry);
    }

    private static final String ADMIN_USERNAME = "admin";

    private void sendAuditNotif(String type, String subject, String message,
                                String refId, String... recipients) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String r : recipients) { if (r != null && !r.isBlank()) seen.add(r); }
        for (String r : seen) {
            notificationProducer.send("audit-events", NotificationEvent.builder()
                    .recipientEmail(r).recipientName(r)
                    .type(type).subject(subject).message(message)
                    .referenceId(refId).referenceType("AUDIT").build());
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> validateComplianceOfficer(Long officerId) {
        ApiResponseDTO<Map<String, Object>> res;
        try {
            res = iamServiceClient.getUserById(officerId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("User", "id", officerId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("IAM Service is currently unavailable. Please try again later.");
        }
        if (IamServiceFallback.MARKER.equals(res.getMessage())) {
            throw new ServiceUnavailableException("IAM Service is currently unavailable. Please try again later.");
        }
        if (!res.isSuccess() || res.getData() == null) {
            throw new ResourceNotFoundException("User", "id", officerId);
        }
        String role = (String) res.getData().get("role");
        if (!"COMPLIANCE_OFFICER".equals(role) && !"ADMIN".equals(role)) {
            throw new BadRequestException(
                    "User ID " + officerId + " is not a COMPLIANCE_OFFICER. Current role: " + role +
                            ". Only COMPLIANCE_OFFICER or ADMIN can be assigned as audit officers.");
        }
        return res.getData();
    }

    private Audit findById(Long id) {
        return auditRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit", "id", id));
    }

    private AuditResponseDTO mapToResponse(Audit a) {
        return AuditResponseDTO.builder()
                .auditId(a.getAuditId())
                .complianceOfficerId(a.getComplianceOfficerId())
                .officerName(a.getOfficerName())
                .scope(a.getScope())
                .findings(a.getFindings())
                .date(a.getDate())
                .auditDate(a.getAuditDate())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private ComplianceAuditLogResponseDTO mapLogToResponse(ComplianceAuditLog l) {
        return ComplianceAuditLogResponseDTO.builder()
                .logId(l.getLogId())
                .complianceRecordId(l.getComplianceRecordId())
                .action(l.getAction())
                .performedBy(l.getPerformedBy())
                .findings(l.getFindings())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
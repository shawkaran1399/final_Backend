package com.buildledger.compliance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity @Table(name = "compliance_audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "compliance_record_id", nullable = false)
    private Long complianceRecordId;

    /** The new status that was applied — e.g. UNDER_REVIEW, PASSED, FAILED */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @CreatedDate @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

package com.buildledger.compliance.entity;

import com.buildledger.compliance.enums.AuditStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "audits")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Audit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    /** Compliance officer user ID from IAM Service */
    @Column(name = "compliance_officer_id", nullable = false)
    private Long complianceOfficerId;

    @Column(name = "officer_name", length = 100)
    private String officerName;

    @Column(name = "scope", nullable = false, length = 300)
    private String scope;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "audit_date")
    private LocalDate auditDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AuditStatus status = AuditStatus.IN_PROGRESS;

    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
}


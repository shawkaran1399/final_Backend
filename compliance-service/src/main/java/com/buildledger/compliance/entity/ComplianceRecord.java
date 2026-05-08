package com.buildledger.compliance.entity;

import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "compliance_records")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compliance_id")
    private Long complianceId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ComplianceType type;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "notes", columnDefinition = "TEXT", nullable = false)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ComplianceStatus status = ComplianceStatus.PENDING;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @CreatedDate @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}


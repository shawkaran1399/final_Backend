package com.buildledger.compliance.repository;

import com.buildledger.compliance.entity.ComplianceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplianceAuditLogRepository extends JpaRepository<ComplianceAuditLog, Long> {
    List<ComplianceAuditLog> findByComplianceRecordIdOrderByCreatedAtAsc(Long complianceRecordId);
}

package com.buildledger.compliance.repository;

import com.buildledger.compliance.entity.ComplianceRecord;
import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplianceRecordRepository extends JpaRepository<ComplianceRecord, Long> {
    List<ComplianceRecord> findByContractId(Long contractId);
    List<ComplianceRecord> findByStatus(ComplianceStatus status);
    List<ComplianceRecord> findByStatusIn(List<ComplianceStatus> statuses);
    boolean existsByContractIdAndTypeAndStatusIn(Long referenceId, ComplianceType type, List<ComplianceStatus> statuses);
}


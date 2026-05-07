package com.buildledger.delivery.repository;

import com.buildledger.delivery.entity.ServiceRecord;
import com.buildledger.delivery.enums.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {
    List<ServiceRecord> findByContractId(Long contractId);
    List<ServiceRecord> findByStatus(ServiceStatus status);
}


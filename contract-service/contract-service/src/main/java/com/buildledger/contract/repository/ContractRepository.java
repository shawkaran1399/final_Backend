package com.buildledger.contract.repository;

import com.buildledger.contract.entity.Contract;
import com.buildledger.contract.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByVendorId(Long vendorId);
    List<Contract> findByProjectId(Long projectId);
    List<Contract> findByStatus(ContractStatus status);
}


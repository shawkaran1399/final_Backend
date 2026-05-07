package com.buildledger.finance.repository;

import com.buildledger.finance.entity.Invoice;
import com.buildledger.finance.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByContractId(Long contractId);
    List<Invoice> findByStatus(InvoiceStatus status);
    List<Invoice> findByStatusInAndDueDateNotNull(List<InvoiceStatus> statuses);
}


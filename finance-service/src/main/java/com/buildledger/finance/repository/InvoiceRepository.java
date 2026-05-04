package com.buildledger.finance.repository;

import com.buildledger.finance.entity.Invoice;
import com.buildledger.finance.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByContractId(Long contractId);
    List<Invoice> findByStatus(InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.contractId = :contractId AND i.status IN :statuses")
    BigDecimal sumAmountByContractIdAndStatuses(@Param("contractId") Long contractId,
                                                @Param("statuses") List<InvoiceStatus> statuses);
}


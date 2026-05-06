package com.buildledger.delivery.repository;

import com.buildledger.delivery.entity.Delivery;
import com.buildledger.delivery.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findByContractId(Long contractId);
    List<Delivery> findByStatus(DeliveryStatus status);
}


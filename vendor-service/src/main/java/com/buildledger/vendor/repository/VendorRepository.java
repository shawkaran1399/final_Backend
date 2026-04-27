package com.buildledger.vendor.repository;

import com.buildledger.vendor.entity.Vendor;
import com.buildledger.vendor.enums.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<Vendor> findByStatus(VendorStatus status);
    Optional<Vendor> findByUserId(Long userId);
}


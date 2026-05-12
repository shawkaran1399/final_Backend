package com.buildledger.delivery.entity;

import com.buildledger.delivery.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "services")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    /** Contract ID validated via Feign Client against contract-service */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    /**
     * Price of this service in INR.
     * Must not exceed remaining contract budget (contractValue - sum of existing delivery+service prices).
     * Used later to generate service invoice.
     */
    @Column(name = "price", precision = 18, scale = 2, nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ServiceStatus status = ServiceStatus.PENDING;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Project Manager username cached from contract-service.
     * Used by DeliveryOverdueScheduler to send overdue notifications to the PM.
     */
    @Column(name = "manager_username", length = 100)
    private String managerUsername;

    /**
     * Vendor username cached from contract-service.
     * Used by DeliveryOverdueScheduler to send overdue notifications to the Vendor.
     */
    @Column(name = "vendor_username", length = 100)
    private String vendorUsername;

    /**
     * Date when last overdue notification was sent.
     * Prevents DeliveryOverdueScheduler from sending duplicate alerts.
     * Null means no notification has been sent yet.
     */
    @Column(name = "last_notified_date")
    private LocalDate lastNotifiedDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
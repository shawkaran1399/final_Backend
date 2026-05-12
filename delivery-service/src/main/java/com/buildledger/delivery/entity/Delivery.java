package com.buildledger.delivery.entity;

import com.buildledger.delivery.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long deliveryId;

    /** Contract ID validated via Feign Client against contract-service */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "item", nullable = false, length = 200)
    private String item;

    @Column(name = "quantity", precision = 18, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 50)
    private String unit;

    /**
     * Price of this delivery milestone in INR.
     * Must not exceed remaining contract budget (contractValue - sum of existing delivery+service prices).
     * Used later to generate delivery invoice.
     */
    @Column(name = "price", precision = 18, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

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
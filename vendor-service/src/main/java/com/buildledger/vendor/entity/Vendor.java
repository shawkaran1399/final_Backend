package com.buildledger.vendor.entity;

import com.buildledger.vendor.enums.VendorStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "vendors")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_id")
    private Long vendorId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "contact_info", length = 200)
    private String contactInfo;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "address", length = 300)
    private String address;

    /** Chosen by vendor during registration. Used as username in IAM after approval. */
    @Column(name = "username", unique = true, length = 50)
    private String username;

    /** BCrypt-encoded password chosen by vendor during registration. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private VendorStatus status = VendorStatus.PENDING;

    /** Populated after document approval — links to the IAM user account */
    @Column(name = "user_id")
    private Long userId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}


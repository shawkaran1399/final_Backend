package com.buildledger.notification.entity;

import com.buildledger.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email") private String recipientEmail;
    @Column(name = "recipient_name") private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "subject") private String subject;
    @Column(name = "message", columnDefinition = "TEXT") private String message;
    @Column(name = "reference_id") private String referenceId;
    @Column(name = "reference_type") private String referenceType;

    @Column(name = "delivered", nullable = false)
    @Builder.Default private Boolean delivered = false;

    @Column(name = "is_read", nullable = false)
    @Builder.Default private Boolean read = false;

    @Column(name = "admin_read", nullable = false)
    @Builder.Default private Boolean adminRead = false;

    @CreatedDate @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
package com.buildledger.notification.repository;

import com.buildledger.notification.entity.Notification;
import com.buildledger.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientEmail(String email);
    List<Notification> findByType(NotificationType type);
    List<Notification> findByDelivered(Boolean delivered);
    long countByRecipientEmailAndReadFalse(String email);
    long countByReadFalse();
}


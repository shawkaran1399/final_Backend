package com.buildledger.notification.service;

import com.buildledger.notification.dto.NotificationEvent;
import com.buildledger.notification.entity.Notification;
import com.buildledger.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles persisting and "delivering" notifications.
 * In production, replace the log statement in deliver() with actual email/SMS/push logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification processEvent(NotificationEvent event) {
        log.info("Processing notification event: type={}, recipient={}", event.getType(), event.getRecipientEmail());

        Notification notification = Notification.builder()
                .recipientEmail(event.getRecipientEmail())
                .recipientName(event.getRecipientName())
                .type(event.getType())
                .subject(event.getSubject())
                .message(event.getMessage())
                .referenceId(event.getReferenceId())
                .referenceType(event.getReferenceType())
                .delivered(false)
                .build();

        Notification saved = notificationRepository.save(notification);

        // Simulate delivery (replace with actual email/push/SMS provider)
        deliver(saved);

        return saved;
    }

    private void deliver(Notification notification) {
        // TODO: Integrate email provider (e.g., SendGrid, JavaMailSender) here
        log.info("=== NOTIFICATION DELIVERED ===");
        log.info("To: {} <{}>", notification.getRecipientName(), notification.getRecipientEmail());
        log.info("Subject: {}", notification.getSubject());
        log.info("Message: {}", notification.getMessage());
        log.info("Type: {} | Ref: {} [{}]", notification.getType(),
                notification.getReferenceId(), notification.getReferenceType());
        log.info("==============================");

        notification.setDelivered(true);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAll() { return notificationRepository.findAll(); }

    @Transactional(readOnly = true)
    public List<Notification> getByEmail(String email) { return notificationRepository.findByRecipientEmail(email); }

    @Transactional(readOnly = true)
    public List<Notification> getPending() { return notificationRepository.findByDelivered(false); }

    @Transactional
    public Notification markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        return notificationRepository.countByRecipientEmailAndRead(email, false);
    }

    @Transactional
    public Notification markAsAdminRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setAdminRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsAdminRead() {
        List<Notification> unread = notificationRepository.findAll().stream()
                .filter(n -> Boolean.FALSE.equals(n.getAdminRead()))
                .toList();
        unread.forEach(n -> n.setAdminRead(true));
        notificationRepository.saveAll(unread);
    }

    public long getAdminUnreadCount() {
        return notificationRepository.countByRecipientEmailAndAdminRead("admin", false);
    }
}
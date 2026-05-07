package com.buildledger.notification.consumer;

import com.buildledger.notification.dto.NotificationEvent;
import com.buildledger.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens to all Kafka notification topics and processes each event.
 * Every other service publishes to these topics when significant events occur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = {
            "vendor-events",
            "contract-events",
            "invoice-events",
            "payment-events",
            "delivery-events",
            "compliance-events",
            "audit-events",
                "iam-events"
        },
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload NotificationEvent event,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        Acknowledgment acknowledgment) {
        log.info("Received Kafka event from topic '{}': type={}", topic, event.getType());
        try {
            notificationService.processEvent(event);
            acknowledgment.acknowledge();
            log.info("Successfully processed notification for topic: {}", topic);
        } catch (Exception e) {
            log.error("Error processing notification from topic '{}': {}", topic, e.getMessage(), e);
            // Do NOT acknowledge — message will be retried based on retry config
        }
    }
}


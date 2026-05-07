package com.buildledger.iam.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void send(String topic, NotificationEvent event) {
        try {
            kafkaTemplate.send(topic, event);
            log.info("Notification sent to topic '{}': type={}", topic, event.getType());
        } catch (Exception e) {
            log.error("Failed to send notification to topic '{}': {}", topic, e.getMessage());
        }
    }
}
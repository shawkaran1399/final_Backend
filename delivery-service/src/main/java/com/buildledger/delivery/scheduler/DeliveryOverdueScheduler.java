package com.buildledger.delivery.scheduler;

import com.buildledger.delivery.entity.Delivery;
import com.buildledger.delivery.entity.ServiceRecord;
import com.buildledger.delivery.enums.DeliveryStatus;
import com.buildledger.delivery.enums.ServiceStatus;
import com.buildledger.delivery.event.NotificationEvent;
import com.buildledger.delivery.event.NotificationProducer;
import com.buildledger.delivery.repository.DeliveryRepository;
import com.buildledger.delivery.repository.ServiceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryOverdueScheduler {

    private final DeliveryRepository deliveryRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final NotificationProducer notificationProducer;

    // ── Delivery Overdue — runs every day at 9:00 AM ─────────────────────────
    // For testing use: "0 * * * * *"  (every minute)
    // For production:  "0 0 9 * * *"  (9 AM daily)
    @Scheduled(cron = "0 * * * * *")
    public void checkDeliveryOverdue() {
        log.info("Running delivery overdue check...");

        LocalDate today = LocalDate.now();

        List<Delivery> activeDeliveries = deliveryRepository
                .findByStatusIn(List.of(DeliveryStatus.PENDING, DeliveryStatus.DELAYED));

        for (Delivery delivery : activeDeliveries) {

            if (delivery.getDate() == null) continue;

            // Send ONLY ONCE — skip if already notified
            if (delivery.getLastNotifiedDate() != null) {
                log.info("Delivery {} already notified — skipping", delivery.getDeliveryId());
                continue;
            }

            // Overdue — expected date has passed
            if (delivery.getDate().isBefore(today)) {
                log.info("Delivery {} is OVERDUE (was due {})",
                        delivery.getDeliveryId(), delivery.getDate());

                // Notify PM
                if (delivery.getManagerUsername() != null
                        && !delivery.getManagerUsername().isEmpty()) {
                    notificationProducer.send("delivery-events", NotificationEvent.builder()
                            .recipientEmail(delivery.getManagerUsername())
                            .recipientName("Project Manager")
                            .type("SCHEDULER_DELIVERY_OVERDUE_ALERT")
                            .subject("OVERDUE: Delivery #" + delivery.getDeliveryId()
                                    + " is past its expected date")
                            .message("Delivery #" + delivery.getDeliveryId()
                                    + " for contract #" + delivery.getContractId()
                                    + " was expected on " + delivery.getDate()
                                    + " and is now OVERDUE. Item: " + delivery.getItem()
                                    + ". Current status: " + delivery.getStatus()
                                    + ". Please follow up with the vendor immediately.")
                            .referenceId(String.valueOf(delivery.getDeliveryId()))
                            .referenceType("DELIVERY")
                            .build());
                }

                // Notify Vendor
                if (delivery.getVendorUsername() != null
                        && !delivery.getVendorUsername().isEmpty()) {
                    notificationProducer.send("delivery-events", NotificationEvent.builder()
                            .recipientEmail(delivery.getVendorUsername())
                            .recipientName("Vendor")
                            .type("SCHEDULER_DELIVERY_OVERDUE_ALERT")
                            .subject("OVERDUE: Your delivery #" + delivery.getDeliveryId()
                                    + " is past its expected date")
                            .message("Your delivery #" + delivery.getDeliveryId()
                                    + " for contract #" + delivery.getContractId()
                                    + " was expected on " + delivery.getDate()
                                    + " and is now OVERDUE. Item: " + delivery.getItem()
                                    + ". Please update the status or contact your project manager.")
                            .referenceId(String.valueOf(delivery.getDeliveryId()))
                            .referenceType("DELIVERY")
                            .build());
                }

                // Mark as notified — never send again
                delivery.setLastNotifiedDate(today);
                deliveryRepository.save(delivery);
            }
        }

        log.info("Delivery overdue check completed.");
    }

    // ── Service Overdue — runs every day at 9:30 AM ──────────────────────────
    // For testing use: "0 * * * * *"  (every minute)
    // For production:  "0 30 9 * * *" (9:30 AM daily)
    @Scheduled(cron = "0 * * * * *")
    public void checkServiceOverdue() {
        log.info("Running service overdue check...");

        LocalDate today = LocalDate.now();

        List<ServiceRecord> activeServices = serviceRecordRepository
                .findByStatusIn(List.of(ServiceStatus.PENDING, ServiceStatus.IN_PROGRESS));

        for (ServiceRecord service : activeServices) {

            if (service.getCompletionDate() == null) continue;

            // Send ONLY ONCE — skip if already notified
            if (service.getLastNotifiedDate() != null) {
                log.info("Service {} already notified — skipping", service.getServiceId());
                continue;
            }

            // Overdue — expected completion date has passed
            if (service.getCompletionDate().isBefore(today)) {
                log.info("Service {} is OVERDUE (was due {})",
                        service.getServiceId(), service.getCompletionDate());

                // Notify PM
                if (service.getManagerUsername() != null
                        && !service.getManagerUsername().isEmpty()) {
                    notificationProducer.send("delivery-events", NotificationEvent.builder()
                            .recipientEmail(service.getManagerUsername())
                            .recipientName("Project Manager")
                            .type("SCHEDULER_SERVICE_OVERDUE_ALERT")
                            .subject("OVERDUE: Service #" + service.getServiceId()
                                    + " is past its completion date")
                            .message("Service #" + service.getServiceId()
                                    + " for contract #" + service.getContractId()
                                    + " was expected to be completed by "
                                    + service.getCompletionDate()
                                    + " and is now OVERDUE. Description: "
                                    + service.getDescription()
                                    + ". Current status: " + service.getStatus()
                                    + ". Please follow up with the vendor immediately.")
                            .referenceId(String.valueOf(service.getServiceId()))
                            .referenceType("SERVICE")
                            .build());
                }

                // Notify Vendor
                if (service.getVendorUsername() != null
                        && !service.getVendorUsername().isEmpty()) {
                    notificationProducer.send("delivery-events", NotificationEvent.builder()
                            .recipientEmail(service.getVendorUsername())
                            .recipientName("Vendor")
                            .type("SCHEDULER_SERVICE_OVERDUE_ALERT")
                            .subject("OVERDUE: Your service #" + service.getServiceId()
                                    + " is past its completion date")
                            .message("Your service #" + service.getServiceId()
                                    + " for contract #" + service.getContractId()
                                    + " was expected to be completed by "
                                    + service.getCompletionDate()
                                    + " and is now OVERDUE. Description: "
                                    + service.getDescription()
                                    + ". Please complete and update the status immediately.")
                            .referenceId(String.valueOf(service.getServiceId()))
                            .referenceType("SERVICE")
                            .build());
                }

                // Mark as notified — never send again
                service.setLastNotifiedDate(today);
                serviceRecordRepository.save(service);
            }
        }

        log.info("Service overdue check completed.");
    }
}
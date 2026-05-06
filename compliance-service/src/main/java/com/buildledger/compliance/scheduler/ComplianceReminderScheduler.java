//package com.buildledger.compliance.scheduler;
//
//import com.buildledger.compliance.entity.ComplianceRecord;
//import com.buildledger.compliance.enums.ComplianceStatus;
//import com.buildledger.compliance.event.NotificationEvent;
//import com.buildledger.compliance.event.NotificationProducer;
//import com.buildledger.compliance.repository.ComplianceRecordRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class ComplianceReminderScheduler {
//
//    private final ComplianceRecordRepository complianceRecordRepository;
//    private final NotificationProducer notificationProducer;
//
//    // Runs every day at 9:00 AM
//    @Scheduled(cron = "0 * * * * *")
//    public void checkComplianceDueDates() {
//        log.info("Running compliance due date check...");
//
//        LocalDate today = LocalDate.now();
//        LocalDate threeDaysFromNow = today.plusDays(3);
//
//        // Only check PENDING and UNDER_REVIEW records
//        List<ComplianceRecord> activeRecords = complianceRecordRepository
//                .findByStatusIn(List.of(ComplianceStatus.PENDING, ComplianceStatus.UNDER_REVIEW));
//
//        for (ComplianceRecord record : activeRecords) {
//
//            // Skip if no date set
//            if (record.getDate() == null) continue;
//
//            String officerName = record.getReviewedBy() != null
//                    ? record.getReviewedBy() : "Compliance Officer";
//
//            // COMPLIANCE_OVERDUE — due date already passed
//            if (record.getDate().isBefore(today)) {
//                log.info("Compliance record {} is OVERDUE (was due {})",
//                        record.getComplianceId(), record.getDate());
//
//                notificationProducer.send("compliance-events", NotificationEvent.builder()
//                        .recipientEmail("")
//                        .recipientName(officerName)
//                        .type("COMPLIANCE_OVERDUE")
//                        .subject("OVERDUE: Compliance record #" + record.getComplianceId())
//                        .message("Compliance record #" + record.getComplianceId()
//                                + " for contract #" + record.getContractId()
//                                + " was due on " + record.getDate()
//                                + " and is now OVERDUE. Current status: " + record.getStatus()
//                                + ". Please take immediate action.")
//                        .referenceId(String.valueOf(record.getComplianceId()))
//                        .referenceType("COMPLIANCE")
//                        .build());
//            }
//
//            // COMPLIANCE_REVIEW_DUE — due within next 3 days
//            else if (!record.getDate().isAfter(threeDaysFromNow)) {
//                log.info("Compliance record {} is due soon on {}",
//                        record.getComplianceId(), record.getDate());
//
//                notificationProducer.send("compliance-events", NotificationEvent.builder()
//                        .recipientEmail("")
//                        .recipientName(officerName)
//                        .type("COMPLIANCE_REVIEW_DUE")
//                        .subject("Reminder: Compliance record #" + record.getComplianceId() + " due soon")
//                        .message("Compliance record #" + record.getComplianceId()
//                                + " for contract #" + record.getContractId()
//                                + " is due on " + record.getDate()
//                                + ". Current status: " + record.getStatus()
//                                + ". Please complete the review before the due date.")
//                        .referenceId(String.valueOf(record.getComplianceId()))
//                        .referenceType("COMPLIANCE")
//                        .build());
//            }
//        }
//
//        log.info("Compliance due date check completed.");
//    }
//}
package com.buildledger.finance.scheduler;

import com.buildledger.finance.entity.Invoice;
import com.buildledger.finance.enums.InvoiceStatus;
import com.buildledger.finance.event.NotificationEvent;
import com.buildledger.finance.event.NotificationProducer;
import com.buildledger.finance.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceOverdueScheduler {

    private final InvoiceRepository invoiceRepository;
    private final NotificationProducer notificationProducer;

    // ── Invoice Due/Overdue Check — runs every day at 10:00 AM ───────────────
    // For testing use: "0 * * * * *"  (every minute)
    // For production:  "0 0 10 * * *" (10 AM daily)
    @Scheduled(cron = "0 * * * * *")
    public void checkInvoiceDueDates() {
        log.info("Running invoice due date check...");

        LocalDate today = LocalDate.now();
        LocalDate threeDaysFromNow = today.plusDays(3);

        // Only check APPROVED and UNDER_REVIEW invoices that have a dueDate
        List<Invoice> activeInvoices = invoiceRepository
                .findByStatusInAndDueDateNotNull(
                        List.of(InvoiceStatus.APPROVED, InvoiceStatus.UNDER_REVIEW));

        for (Invoice invoice : activeInvoices) {

            if (invoice.getDueDate() == null) continue;

            // Skip if already notified — send ONLY ONCE
            if (invoice.getLastNotifiedDate() != null) {
                log.info("Invoice {} already notified — skipping", invoice.getInvoiceId());
                continue;
            }

            String vendorUsername = invoice.getVendorUsername() != null
                    ? invoice.getVendorUsername() : "";
            String vendorName = invoice.getVendorName() != null
                    ? invoice.getVendorName() : "Vendor";

            // INVOICE_OVERDUE — due date already passed and invoice not PAID
            if (invoice.getDueDate().isBefore(today)) {
                log.info("Invoice {} is OVERDUE (was due {})",
                        invoice.getInvoiceId(), invoice.getDueDate());

                notificationProducer.send("invoice-events", NotificationEvent.builder()
                        .recipientEmail(vendorUsername)
                        .recipientName(vendorName)
                        .type("SCHEDULER_INVOICE_OVERDUE_ALERT")
                        .subject("OVERDUE: Invoice #" + invoice.getInvoiceId()
                                + " is past its due date")
                        .message("Dear " + vendorName + ", invoice #" + invoice.getInvoiceId()
                                + " for amount " + invoice.getAmount()
                                + " was due on " + invoice.getDueDate()
                                + " and has NOT been paid yet."
                                + " Current status: " + invoice.getStatus()
                                + ". Please contact admin immediately.")
                        .referenceId(String.valueOf(invoice.getInvoiceId()))
                        .referenceType("INVOICE")
                        .build());

                // Mark as notified — never send again
                invoice.setLastNotifiedDate(today);
                invoiceRepository.save(invoice);
            }

            // INVOICE_DUE_REMINDER — due within next 3 days
            else if (!invoice.getDueDate().isAfter(threeDaysFromNow)) {
                log.info("Invoice {} is due soon on {}",
                        invoice.getInvoiceId(), invoice.getDueDate());

                notificationProducer.send("invoice-events", NotificationEvent.builder()
                        .recipientEmail(vendorUsername)
                        .recipientName(vendorName)
                        .type("SCHEDULER_INVOICE_DUE_REMINDER")
                        .subject("Reminder: Invoice #" + invoice.getInvoiceId()
                                + " is due soon")
                        .message("Dear " + vendorName + ", invoice #" + invoice.getInvoiceId()
                                + " for amount " + invoice.getAmount()
                                + " is due on " + invoice.getDueDate()
                                + ". Current status: " + invoice.getStatus()
                                + ". Please ensure payment is processed before the due date.")
                        .referenceId(String.valueOf(invoice.getInvoiceId()))
                        .referenceType("INVOICE")
                        .build());

                // Mark as notified — never send again
                invoice.setLastNotifiedDate(today);
                invoiceRepository.save(invoice);
            }
        }

        log.info("Invoice due date check completed.");
    }
}
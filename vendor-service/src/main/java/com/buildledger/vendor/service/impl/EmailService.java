package com.buildledger.vendor.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    // ← Only when admin APPROVES document
    public void sendActivationEmail(String toEmail, String name,
                                    String username) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("BuildLedger — Your Vendor Account is Now ACTIVE!");
            message.setText(
                    "Dear " + name + ",\n\n"
                            + "Great news! Your vendor account has been approved "
                            + "and is now ACTIVE.\n\n"
                            + "You can now log in using your credentials:\n"
                            + "You can now:\n"
                            + "- View and manage your contracts\n"
                            + "- Submit invoices\n"
                            + "- Track your deliveries\n"
                            + "- Manage your service records\n\n"
                            + "Login now and start working!\n\n"
                            + "Regards,\n"
                            + "BuildLedger Team"
            );
            mailSender.send(message);
            log.info("Activation email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send activation email to {}: {}",
                    toEmail, e.getMessage());
        }
    }

    // ← Only when admin REJECTS document
    public void sendSuspensionEmail(String toEmail, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(
                    "BuildLedger — Your Vendor Account has been Rejected");
            message.setText(
                    "Dear " + name + ",\n\n"
                            + "We regret to inform you that your vendor account has been "
                            + "suspended due to rejected document verification.\n\n"
                            + "Reason: Your submitted documents did not pass our "
                            + "verification process.\n\n"
                            + "What you can do:\n"
                            + "1. Contact our support team for more information\n"
                            + "Please contact support as soon as possible.\n\n"
                            + "Regards,\n"
                            + "BuildLedger Team"
            );
            mailSender.send(message);
            log.info("Suspension email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send suspension email to {}: {}",
                    toEmail, e.getMessage());
        }
    }
}
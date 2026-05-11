package com.buildledger.iam.service;

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

    public void sendAccountCreatedEmail(String toEmail, String name,
                                        String username, String password,
                                        String role) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Welcome to BuildLedger — Your Account is Ready");
            message.setText(
                    "Dear " + name + ",\n\n"
                            + "Your BuildLedger account has been created successfully.\n\n"
                            + "Here are your login credentials:\n"
                            + "──────────────────────────────\n"
                            + "Username : " + username + "\n"
                            + "Password : " + password + "\n"
                            + "Role     : " + role + "\n"
                            + "──────────────────────────────\n\n"
                            + "Please login and change your password immediately.\n\n"
                            + "Regards,\n"
                            + "BuildLedger Team"
            );
            mailSender.send(message);
            log.info("Account creation email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendAccountStatusEmail(String toEmail, String name, String status) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);

            if ("INACTIVE".equals(status)) {
                message.setSubject("BuildLedger — Your Account has been Deactivated");
                message.setText(
                        "Dear " + name + ",\n\n"
                                + "Your BuildLedger account has been deactivated by the admin.\n\n"
                                + "You will not be able to log in until your account is reactivated.\n\n"
                                + "If you think this is a mistake please contact your admin.\n\n"
                                + "Regards,\n"
                                + "BuildLedger Team"
                );
            } else if ("ACTIVE".equals(status)) {
                message.setSubject("BuildLedger — Your Account has been Reactivated");
                message.setText(
                        "Dear " + name + ",\n\n"
                                + "Good news! Your BuildLedger account has been reactivated by the admin.\n\n"
                                + "You can now log in and continue using the system.\n\n"
                                + "Regards,\n"
                                + "BuildLedger Team"
                );
            }

            mailSender.send(message);
            log.info("Account status email sent to: {} — status: {}", toEmail, status);
        } catch (Exception e) {
            log.error("Failed to send status email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ← ADD THIS METHOD
    public void sendPasswordChangedEmail(String toEmail, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("BuildLedger — Password Changed Successfully");
            message.setText(
                    "Dear " + name + ",\n\n"
                            + "Your BuildLedger account password has been changed successfully.\n\n"
                            + "If you did not make this change please contact admin immediately.\n\n"
                            + "Regards,\n"
                            + "BuildLedger Team"
            );
            mailSender.send(message);
            log.info("Password changed email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password changed email to {}: {}",
                    toEmail, e.getMessage());
        }
    }
}
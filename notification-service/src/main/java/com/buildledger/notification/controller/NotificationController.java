package com.buildledger.notification.controller;
import org.springframework.security.core.Authentication;
import com.buildledger.notification.entity.Notification;
import com.buildledger.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all notifications [ADMIN only]")
    public ResponseEntity<List<Notification>> getAll() {
        return ResponseEntity.ok(notificationService.getAll());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get undelivered notifications [ADMIN only]")
    public ResponseEntity<List<Notification>> getPending() {
        return ResponseEntity.ok(notificationService.getPending());
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my own notifications [All roles]")
    public ResponseEntity<List<Notification>> getMyNotifications(Authentication authentication) {
        String identifier = authentication.getName();
        return ResponseEntity.ok(notificationService.getByEmail(identifier));
    }

    @GetMapping("/recipient/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get notifications by recipient email [ADMIN only]")
    public ResponseEntity<List<Notification>> getByEmail(@PathVariable String email) {
        return ResponseEntity.ok(notificationService.getByEmail(email));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark notification as read [All roles]")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread notification count [All roles]")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(notificationService.getUnreadCount(email));
    }

    @PatchMapping("/{id}/admin-read")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark notification as admin-read [ADMIN only] — does not affect recipient read status")
    public ResponseEntity<Notification> markAsAdminRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsAdminRead(id));
    }

    @PatchMapping("/admin-read-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark all notifications as admin-read [ADMIN only]")
    public ResponseEntity<Void> markAllAsAdminRead() {
        notificationService.markAllAsAdminRead();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin-unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get total system unread count for admin [ADMIN only]")
    public ResponseEntity<Long> getAdminUnreadCount() {
        return ResponseEntity.ok(notificationService.getAdminUnreadCount());
    }
}
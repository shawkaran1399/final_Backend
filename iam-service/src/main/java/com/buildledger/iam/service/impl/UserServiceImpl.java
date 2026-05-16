package com.buildledger.iam.service.impl;

import com.buildledger.iam.dto.request.CreateUserRequestDTO;
import com.buildledger.iam.dto.request.UpdateUserRequestDTO;
import com.buildledger.iam.dto.response.UserResponseDTO;
import com.buildledger.iam.entity.User;
import com.buildledger.iam.enums.Role;
import com.buildledger.iam.enums.UserStatus;
import com.buildledger.iam.event.NotificationEvent;
import com.buildledger.iam.event.NotificationProducer;
import com.buildledger.iam.exception.BadRequestException;
import com.buildledger.iam.exception.DuplicateResourceException;
import com.buildledger.iam.exception.ResourceNotFoundException;
import com.buildledger.iam.repository.UserRepository;
import com.buildledger.iam.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationProducer notificationProducer;  // ← ADD

    @Override
    public UserResponseDTO createUser(CreateUserRequestDTO request) {
        log.info("Creating user: {} with role: {}", request.getUsername(), request.getRole());

        if (request.getRole() == Role.ADMIN || request.getRole() == Role.VENDOR) {
            throw new BadRequestException(
                    "Role " + request.getRole() + " cannot be assigned through this endpoint. " +
                            "ADMIN is provisioned at bootstrap; VENDOR is auto-assigned after document approval."
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(request.getRole())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: id={}, username={}, role={}", saved.getUserId(), saved.getUsername(), saved.getRole());

        String userCreatedSubject = "Your BuildLedger account has been created";
        String userCreatedMessage = "Dear " + saved.getName() + ", your account has been created successfully. "
                + "Username: " + saved.getUsername()
                + ", Role: " + saved.getRole()
                + ". You can now log in to BuildLedger.";
        String userCreatedRefId = String.valueOf(saved.getUserId());

        // Notify the created user
        notificationProducer.send("iam-events", NotificationEvent.builder()
                .recipientEmail(saved.getUsername())
                .recipientName(saved.getName())
                .type("USER_CREATED")
                .subject(userCreatedSubject)
                .message(userCreatedMessage)
                .referenceId(userCreatedRefId)
                .referenceType("USER")
                .build());

        // Notify admin (skip if the created user IS admin to avoid duplicate)
        if (!"admin".equals(saved.getUsername())) {
            notificationProducer.send("iam-events", NotificationEvent.builder()
                    .recipientEmail("admin")
                    .recipientName("System Administrator")
                    .type("USER_CREATED")
                    .subject("New user created: " + saved.getUsername())
                    .message("A new user has been created. Name: " + saved.getName()
                            + ", Username: " + saved.getUsername()
                            + ", Role: " + saved.getRole() + ".")
                    .referenceId(userCreatedRefId)
                    .referenceType("USER")
                    .build());
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long userId) {
        return mapToResponse(findById(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserByUsername(String username) {
        return mapToResponse(
                userRepository.findByUsername(username)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "username", username))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUsersByRole(Role role) {
        return userRepository.findByRole(role).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public UserResponseDTO updateUser(Long userId, UpdateUserRequestDTO request) {
        User user = findById(userId);

        boolean statusChanged  = request.getStatus() != null
                && request.getStatus() != user.getStatus();
        boolean profileChanged = (request.getName()  != null)
                || (request.getEmail() != null)
                || (request.getPhone() != null);
        UserStatus oldStatus = user.getStatus();

        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null) {
            if (!request.getEmail().equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getStatus() != null) user.setStatus(request.getStatus());

        UserResponseDTO result = mapToResponse(userRepository.save(user));

        // USER_UPDATED — only fires when profile fields (name/email/phone) changed,
        // not when the request only changed status
        if (profileChanged) {
            notificationProducer.send("iam-events", NotificationEvent.builder()
                    .recipientEmail(user.getUsername())  // username — matches auth.getName()
                    .recipientName(user.getName())
                    .type("USER_UPDATED")
                    .subject("Your account details have been updated")
                    .message("Dear " + user.getName() + ", your account details have been updated by the admin.")
                    .referenceId(String.valueOf(userId))
                    .referenceType("USER")
                    .build());
            notificationProducer.send("iam-events", NotificationEvent.builder()
                    .recipientEmail("admin").recipientName("System Administrator")
                    .type("USER_UPDATED")
                    .subject("User updated: " + user.getUsername())
                    .message("User '" + user.getUsername() + "' profile has been updated.")
                    .referenceId(String.valueOf(userId))
                    .referenceType("USER")
                    .build());
        }

        // USER_STATUS_CHANGED — fires only when status specifically changed
        if (statusChanged) {
            notificationProducer.send("iam-events", NotificationEvent.builder()
                    .recipientEmail(user.getUsername())
                    .recipientName(user.getName())
                    .type("USER_STATUS_CHANGED")
                    .subject("Your account status has changed")
                    .message("Dear " + user.getName() + ", your account status has been changed from "
                            + oldStatus + " to " + request.getStatus() + ".")
                    .referenceId(String.valueOf(userId))
                    .referenceType("USER")
                    .build());
            notificationProducer.send("iam-events", NotificationEvent.builder()
                    .recipientEmail("admin").recipientName("System Administrator")
                    .type("USER_STATUS_CHANGED")
                    .subject("User status changed: " + user.getUsername())
                    .message("User '" + user.getUsername() + "' status changed from " + oldStatus + " to " + request.getStatus() + ".")
                    .referenceId(String.valueOf(userId))
                    .referenceType("USER")
                    .build());
        }

        return result;
    }

    public void deleteUser(Long userId) {
        User user = findById(userId);

        // Capture details BEFORE deleting
        String username = user.getUsername();
        String name     = user.getName();

        // Notify deleted user
        notificationProducer.send("iam-events", NotificationEvent.builder()
                .recipientEmail(username)
                .recipientName(name)
                .type("USER_DELETED")
                .subject("Your BuildLedger account has been deleted")
                .message("Dear " + name + ", your BuildLedger account has been permanently deleted. "
                        + "Please contact support if this was unexpected.")
                .referenceId(String.valueOf(userId))
                .referenceType("USER")
                .build());

        // Notify admin
        notificationProducer.send("iam-events", NotificationEvent.builder()
                .recipientEmail("admin")
                .recipientName("System Administrator")
                .type("USER_DELETED")
                .subject("User deleted: " + username)
                .message("User '" + username + "' (Name: " + name + ") has been permanently deleted.")
                .referenceId(String.valueOf(userId))
                .referenceType("USER")
                .build());

        userRepository.delete(user);
        log.info("User deleted: id={}, username={}", userId, username);
    }

    @Override
    public UserResponseDTO createVendorUser(String username, String encodedPassword,
                                            String name, String email, String phone) {
        String finalUsername = ensureUniqueUsername(username);

        User user = User.builder()
                .username(finalUsername)
                .password(encodedPassword)
                .name(name)
                .role(Role.VENDOR)
                .email(userRepository.existsByEmail(email) ? null : email)
                .phone(phone)
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        log.info("Vendor user created: id={}, username={}", saved.getUserId(), saved.getUsername());
        return mapToResponse(saved);
    }

    private String ensureUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) return base;
        int suffix = 1;
        while (userRepository.existsByUsername(base + "_" + suffix)) suffix++;
        return base + "_" + suffix;
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserResponseDTO mapToResponse(User user) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
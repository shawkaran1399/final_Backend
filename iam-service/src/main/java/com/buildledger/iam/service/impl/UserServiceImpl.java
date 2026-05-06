package com.buildledger.iam.service.impl;

import com.buildledger.iam.dto.request.CreateUserRequestDTO;
import com.buildledger.iam.dto.request.UpdateUserRequestDTO;
import com.buildledger.iam.dto.response.UserResponseDTO;
import com.buildledger.iam.entity.User;
import com.buildledger.iam.enums.Role;
import com.buildledger.iam.enums.UserStatus;
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

    @Override
    public UserResponseDTO createUser(CreateUserRequestDTO request) {
        log.info("Creating user: {} with role: {}", request.getUsername(), request.getRole());

        // ADMIN and VENDOR are privileged roles – cannot be created via this endpoint
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

        return mapToResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.delete(findById(userId));
        log.info("User deleted: id={}", userId);
    }

    @Override
    public UserResponseDTO createVendorUser(String username, String encodedPassword,
                                             String name, String email, String phone) {
        // Ensure username uniqueness
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


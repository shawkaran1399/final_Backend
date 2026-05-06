package com.buildledger.vendor.service.impl;

import com.buildledger.vendor.dto.request.VendorLoginRequestDTO;
import com.buildledger.vendor.dto.response.VendorLoginResponseDTO;
import com.buildledger.vendor.entity.Vendor;
import com.buildledger.vendor.enums.VendorStatus;
import com.buildledger.vendor.exception.BadRequestException;
import com.buildledger.vendor.exception.ResourceNotFoundException;
import com.buildledger.vendor.repository.VendorRepository;
import com.buildledger.vendor.security.JwtUtils;
import com.buildledger.vendor.service.VendorAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VendorAuthServiceImpl implements VendorAuthService {

    private final VendorRepository vendorRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpiration;

    @Override
    public VendorLoginResponseDTO loginPendingVendor(VendorLoginRequestDTO request) {
        log.info("Pending vendor login attempt: {}", request.getUsername());

        // Find vendor by username
        Vendor vendor = vendorRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("Vendor", "username", request.getUsername()));

        // Only PENDING vendors can login
        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new BadRequestException(
                "Only vendors with PENDING status can login. Your status is: " + vendor.getStatus());
        }

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), vendor.getPasswordHash())) {
            log.warn("Invalid password for vendor: {}", request.getUsername());
            throw new BadRequestException("Invalid username or password");
        }

        // Generate JWT token
        String jwt = jwtUtils.generateToken(vendor.getUsername(), vendor.getVendorId(), vendor.getStatus().name());

        log.info("Pending vendor {} logged in successfully", vendor.getUsername());

        return VendorLoginResponseDTO.builder()
            .accessToken(jwt)
            .tokenType("Bearer")
            .expiresIn(jwtExpiration / 1000)
            .vendorId(vendor.getVendorId())
            .username(vendor.getUsername())
            .name(vendor.getName())
            .email(vendor.getEmail())
            .status(vendor.getStatus())
            .build();
    }
}


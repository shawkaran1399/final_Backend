package com.buildledger.iam.controller;

import com.buildledger.iam.dto.request.CreateUserRequestDTO;
import com.buildledger.iam.dto.request.UpdateUserRequestDTO;
import com.buildledger.iam.dto.response.ApiResponseDTO;
import com.buildledger.iam.dto.response.UserResponseDTO;
import com.buildledger.iam.enums.Role;
import com.buildledger.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user (PROJECT_MANAGER, FINANCE_OFFICER, COMPLIANCE_OFFICER) [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> createUser(
            @Valid @RequestBody CreateUserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("User created successfully", userService.createUser(request)));
    }

    @GetMapping
    @Operation(summary = "Get all users [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponseDTO.success("Users retrieved", userService.getAllUsers()));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponseDTO.success("User retrieved", userService.getUserById(userId)));
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get user by username [ALL roles]")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponseDTO.success("User retrieved", userService.getUserByUsername(username)));
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> getUsersByRole(@PathVariable Role role) {
        return ResponseEntity.ok(ApiResponseDTO.success("Users retrieved", userService.getUsersByRole(role)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("User updated", userService.updateUser(userId, request)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<Void>> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponseDTO.success("User deleted successfully"));
    }

    /**
     * Internal endpoint called by Vendor Service after document approval.
     * Creates a VENDOR-role user account.
     */
    @PostMapping("/internal/vendor")
    @Operation(summary = "Create vendor user account [INTERNAL – called by Vendor Service]",
               description = "This endpoint is for internal service-to-service communication only.")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> createVendorUser(
            @RequestParam String username,
            @RequestParam String encodedPassword,
            @RequestParam String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Vendor user created",
                userService.createVendorUser(username, encodedPassword, name, email, phone)));
    }
}


package com.buildledger.iam.service;

import com.buildledger.iam.dto.request.ChangePasswordRequestDTO;
import com.buildledger.iam.dto.request.CreateUserRequestDTO;
import com.buildledger.iam.dto.request.UpdateUserRequestDTO;
import com.buildledger.iam.dto.response.UserResponseDTO;
import com.buildledger.iam.enums.Role;

import java.util.List;

public interface UserService {
    UserResponseDTO createUser(CreateUserRequestDTO request);
    UserResponseDTO getUserById(Long userId);
    UserResponseDTO getUserByUsername(String username);
    List<UserResponseDTO> getAllUsers();
    List<UserResponseDTO> getUsersByRole(Role role);
    UserResponseDTO updateUser(Long userId, UpdateUserRequestDTO request);
    void deleteUser(Long userId);
    void changePassword(ChangePasswordRequestDTO request);

    /** Called internally by vendor-service after document approval */
    UserResponseDTO createVendorUser(String username, String encodedPassword,
                                     String name, String email, String phone);
}


package com.buildledger.iam.dto.response;

import com.buildledger.iam.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String username;
    private String name;
    private Role role;
    private Boolean requiresPasswordChange;   // ← ADD
    private String message;                   // ← ADD
}
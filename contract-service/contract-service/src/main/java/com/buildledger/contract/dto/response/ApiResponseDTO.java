package com.buildledger.contract.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiResponseDTO<T> {
    private boolean success;
    private String message;
    private T data;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponseDTO<T> success(String message, T data) {
        return ApiResponseDTO.<T>builder().success(true).message(message).data(data).build();
    }
    public static <T> ApiResponseDTO<T> success(String message) {
        return ApiResponseDTO.<T>builder().success(true).message(message).build();
    }
}


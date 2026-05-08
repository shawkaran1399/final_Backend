package com.buildledger.compliance.feign;

import com.buildledger.compliance.dto.response.ApiResponseDTO;
import java.util.Map;

// Disabled — INVOICE_CHECK removed; retained for compilation safety
public class FinanceServiceFallback implements FinanceServiceClient {

    public static final String MARKER = "FINANCE_SERVICE_UNAVAILABLE";

    @Override
    public ApiResponseDTO<Map<String, Object>> getInvoiceById(Long invoiceId) {
        return ApiResponseDTO.<Map<String, Object>>builder().success(false).message(MARKER).build();
    }
}

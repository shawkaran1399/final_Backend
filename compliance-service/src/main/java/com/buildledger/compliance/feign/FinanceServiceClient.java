package com.buildledger.compliance.feign;

import com.buildledger.compliance.dto.response.ApiResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

// Disabled — INVOICE_CHECK removed; retained for compilation safety
public interface FinanceServiceClient {

    @GetMapping("/api/invoices/{invoiceId}")
    ApiResponseDTO<Map<String, Object>> getInvoiceById(@PathVariable("invoiceId") Long invoiceId);
}

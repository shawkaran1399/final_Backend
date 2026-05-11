package com.buildledger.report.controller;

import com.buildledger.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Report Generation")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    // ─── Existing raw-data report endpoints ──────────────────────────────────

    @GetMapping("/projects")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Generate project report [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<Map<String, Object>> getProjectReport() {
        return ResponseEntity.ok(reportService.generateProjectReport());
    }

    @GetMapping("/contracts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Generate contract report [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<Map<String, Object>> getContractReport() {
        return ResponseEntity.ok(reportService.generateContractReport());
    }

    @GetMapping("/financial")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FINANCE_OFFICER')")
    @Operation(summary = "Generate financial report [ADMIN / FINANCE_OFFICER]")
    public ResponseEntity<Map<String, Object>> getFinancialReport() {
        return ResponseEntity.ok(reportService.generateFinancialReport());
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Generate delivery report [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<Map<String, Object>> getDeliveryReport() {
        return ResponseEntity.ok(reportService.generateDeliveryReport());
    }

    // ─── Page-level summary endpoints ────────────────────────────────────────

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER') or hasRole('FINANCE_OFFICER')")
    @Operation(summary = "Get dashboard KPI summary [ADMIN / PROJECT_MANAGER / FINANCE_OFFICER]")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        return ResponseEntity.ok(reportService.generateDashboardSummary());
    }

    @GetMapping("/contracts/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Get contract page summary [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<Map<String, Object>> getContractPageSummary() {
        return ResponseEntity.ok(reportService.generateContractPageSummary());
    }

    @GetMapping("/invoices/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FINANCE_OFFICER') or hasRole('VENDOR')")
    @Operation(summary = "Get invoice page summary [ADMIN / FINANCE_OFFICER / VENDOR]")
    public ResponseEntity<Map<String, Object>> getInvoicePageSummary() {
        return ResponseEntity.ok(reportService.generateInvoicePageSummary());
    }

    @GetMapping("/vendors/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get vendor page summary [ADMIN]")
    public ResponseEntity<Map<String, Object>> getVendorPageSummary() {
        return ResponseEntity.ok(reportService.generateVendorPageSummary());
    }

    @GetMapping("/projects/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Get project page summary [ADMIN / PROJECT_MANAGER]")
    public ResponseEntity<Map<String, Object>> getProjectPageSummary() {
        return ResponseEntity.ok(reportService.generateProjectPageSummary());
    }

    @GetMapping("/deliveries/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER') or hasRole('VENDOR')")
    @Operation(summary = "Get delivery page summary [ADMIN / PROJECT_MANAGER / VENDOR]")
    public ResponseEntity<Map<String, Object>> getDeliveryPageSummary() {
        return ResponseEntity.ok(reportService.generateDeliveryPageSummary());
    }

    @GetMapping("/compliance/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Get compliance page summary [ADMIN / COMPLIANCE_OFFICER]")
    public ResponseEntity<Map<String, Object>> getCompliancePageSummary() {
        return ResponseEntity.ok(reportService.generateCompliancePageSummary());
    }
}

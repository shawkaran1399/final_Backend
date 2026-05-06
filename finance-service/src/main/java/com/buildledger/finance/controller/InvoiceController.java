package com.buildledger.finance.controller;

import com.buildledger.finance.dto.request.InvoiceRequestDTO;
import com.buildledger.finance.dto.response.ApiResponseDTO;
import com.buildledger.finance.dto.response.InvoiceResponseDTO;
import com.buildledger.finance.enums.InvoiceStatus;
import com.buildledger.finance.service.FinanceService;
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

@RestController @RequestMapping("/invoices")
@RequiredArgsConstructor @Tag(name = "Invoice Management") @SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final FinanceService financeService;

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('FINANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Submit invoice [VENDOR / FINANCE_OFFICER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<InvoiceResponseDTO>> submitInvoice(@Valid @RequestBody InvoiceRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Invoice submitted for review", financeService.submitInvoice(req)));
    }

    @GetMapping
    @Operation(summary = "Get all invoices [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<InvoiceResponseDTO>>> getAllInvoices() {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoices retrieved", financeService.getAllInvoices()));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get invoice by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<InvoiceResponseDTO>> getInvoiceById(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice retrieved", financeService.getInvoiceById(invoiceId)));
    }

    @GetMapping("/contract/{contractId}")
    @Operation(summary = "Get invoices by contract [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<InvoiceResponseDTO>>> getByContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoices retrieved", financeService.getInvoicesByContract(contractId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get invoices by status [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<InvoiceResponseDTO>>> getByStatus(@PathVariable InvoiceStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoices retrieved", financeService.getInvoicesByStatus(status)));
    }

    @PatchMapping("/{invoiceId}/approve")
    @PreAuthorize("hasRole('FINANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Approve invoice [FINANCE_OFFICER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<InvoiceResponseDTO>> approveInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice approved", financeService.approveInvoice(invoiceId)));
    }

    @PatchMapping("/{invoiceId}/reject")
    @PreAuthorize("hasRole('FINANCE_OFFICER') or hasRole('ADMIN')")
    @Operation(summary = "Reject invoice [FINANCE_OFFICER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<InvoiceResponseDTO>> rejectInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice rejected", financeService.rejectInvoice(invoiceId, reason)));
    }

    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete invoice [ADMIN only] – cannot delete PAID invoices")
    public ResponseEntity<ApiResponseDTO<Void>> deleteInvoice(@PathVariable Long invoiceId) {
        financeService.deleteInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice deleted"));
    }
}


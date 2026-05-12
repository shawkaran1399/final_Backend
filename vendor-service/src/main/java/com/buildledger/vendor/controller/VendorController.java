package com.buildledger.vendor.controller;

import com.buildledger.vendor.dto.request.CreateVendorRequestDTO;
import com.buildledger.vendor.dto.request.UpdateVendorRequestDTO;
import com.buildledger.vendor.dto.request.VendorLoginRequestDTO;
import com.buildledger.vendor.dto.response.ApiResponseDTO;
import com.buildledger.vendor.dto.response.VendorDocumentResponseDTO;
import com.buildledger.vendor.dto.response.VendorLoginResponseDTO;
import com.buildledger.vendor.dto.response.VendorResponseDTO;
import com.buildledger.vendor.enums.DocumentType;
import com.buildledger.vendor.enums.VendorStatus;
import com.buildledger.vendor.enums.VerificationStatus;
import com.buildledger.vendor.service.VendorAuthService;
import com.buildledger.vendor.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vendor Management")
@SecurityRequirement(name = "bearerAuth")
public class VendorController {

    private final VendorService vendorService;
    private final VendorAuthService vendorAuthService;

    @PostMapping("/auth/login")
    @Operation(summary = "Pending vendor login [PUBLIC]")
    public ResponseEntity<ApiResponseDTO<VendorLoginResponseDTO>> loginPendingVendor(
            @Valid @RequestBody VendorLoginRequestDTO request) {
        return ResponseEntity.ok(
            ApiResponseDTO.success("Login successful", vendorAuthService.loginPendingVendor(request))
        );
    }

    @PostMapping("/register")
    @Operation(summary = "Vendor self-registration [PUBLIC]")
    public ResponseEntity<ApiResponseDTO<VendorResponseDTO>> registerVendor(
            @Valid @RequestBody CreateVendorRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
            "Vendor registered successfully. Please upload your documents for verification.",
            vendorService.registerVendor(request)));
    }

    @GetMapping
    @Operation(summary = "Get all vendors [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<VendorResponseDTO>>> getAllVendors() {
        return ResponseEntity.ok(ApiResponseDTO.success("Vendors retrieved", vendorService.getAllVendors()));
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "Get vendor by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<VendorResponseDTO>> getVendorById(@PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Vendor retrieved", vendorService.getVendorById(vendorId)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get vendors by status [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<VendorResponseDTO>>> getVendorsByStatus(
            @PathVariable VendorStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Vendors retrieved", vendorService.getVendorsByStatus(status)));
    }

    @PutMapping("/{vendorId}")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    @Operation(summary = "Update vendor details [VENDOR / ADMIN]")
    public ResponseEntity<ApiResponseDTO<VendorResponseDTO>> updateVendor(
            @PathVariable Long vendorId,
            @Valid @RequestBody UpdateVendorRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Vendor updated successfully",
            vendorService.updateVendor(vendorId, request)));
    }

    @DeleteMapping("/{vendorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a vendor [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<Void>> deleteVendor(@PathVariable Long vendorId) {
        vendorService.deleteVendor(vendorId);
        return ResponseEntity.ok(ApiResponseDTO.success("Vendor deleted successfully"));
    }

    // ── Documents ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/{vendorId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload compliance document PDF [PUBLIC – vendor submits before approval]")
    public ResponseEntity<ApiResponseDTO<VendorDocumentResponseDTO>> uploadDocument(
            @PathVariable Long vendorId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType,
            @RequestParam(value = "remarks", required = false) String remarks) {
        VendorDocumentResponseDTO response = vendorService.uploadDocument(
            vendorId, file, docType, remarks, "PUBLIC_USER");
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponseDTO.success("Document uploaded successfully. Awaiting review.", response));
    }

    @GetMapping("/{vendorId}/documents")
    @Operation(summary = "Get document for a vendor [ALL roles]")
    public ResponseEntity<ApiResponseDTO<VendorDocumentResponseDTO>> getVendorDocument(
            @PathVariable Long vendorId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Document retrieved",
            vendorService.getVendorDocument(vendorId)));
    }

    @GetMapping("/documents/status/{status}")
    @PreAuthorize("hasRole('PROJECT_MANAGER') or hasRole('ADMIN')")
    @Operation(summary = "Get documents by verification status [PROJECT_MANAGER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<List<VendorDocumentResponseDTO>>> getDocumentsByStatus(
            @PathVariable VerificationStatus status) {
        return ResponseEntity.ok(ApiResponseDTO.success("Documents retrieved",
            vendorService.getDocumentsByStatus(status)));
    }

    @GetMapping(value = "/documents/{documentId}/file-url")
    @PreAuthorize("hasRole('PROJECT_MANAGER') or hasRole('ADMIN')")
    @Operation(summary = "Get document file URL by document ID [ADMIN / PROJECT_MANAGER]",
               description = "Returns the stored file URI for a given document ID.")
    public ResponseEntity<ApiResponseDTO<java.util.Map<String, Object>>> getDocumentFileUrl(
            @PathVariable Long documentId) {
        String fileUrl = vendorService.getDocumentFileUrl(documentId);
        java.util.Map<String, Object> data = java.util.Map.of(
            "documentId", documentId,
            "fileUrl", fileUrl
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Document file URL retrieved", data));
    }

    @GetMapping(value = "/documents/{documentId}/download")
    @PreAuthorize("hasRole('PROJECT_MANAGER') or hasRole('ADMIN') or hasRole('VENDOR') or hasRole('COMPLIANCE_OFFICER')")
    @Operation(summary = "Download vendor document PDF [PM / ADMIN / VENDOR / COMPLIANCE_OFFICER]")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId) {
        Resource resource = vendorService.downloadDocument(documentId);
        String filename = resource.getFilename() != null
            ? resource.getFilename().replaceAll(".*[\\\\/]", "") : "document.pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.EXPIRES, "0")
            .body(resource);
    }

    @PutMapping(value = "/{vendorId}/documents/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace vendor document [PUBLIC – only if PENDING and not yet reviewed]")
    public ResponseEntity<ApiResponseDTO<VendorDocumentResponseDTO>> replaceDocument(
            @PathVariable Long vendorId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType,
            @RequestParam(value = "remarks", required = false) String remarks) {
        return ResponseEntity.ok(ApiResponseDTO.success("Document replaced successfully. Awaiting review.",
            vendorService.replaceDocument(vendorId, file, docType, remarks)));
    }

    @PutMapping("/documents/{documentId}/review")
    @PreAuthorize("hasRole('PROJECT_MANAGER') or hasRole('ADMIN')")
    @Operation(summary = "Review vendor document – APPROVED or REJECTED [PROJECT_MANAGER / ADMIN]")
    public ResponseEntity<ApiResponseDTO<VendorDocumentResponseDTO>> reviewDocument(
            @PathVariable Long documentId,
            @RequestParam VerificationStatus status,
            @RequestParam(required = false) String reviewRemarks,
            @RequestHeader("X-Username") String reviewerUsername) {
        VendorDocumentResponseDTO response = vendorService.reviewDocument(
            documentId, status, reviewRemarks, reviewerUsername);
        String message = status == VerificationStatus.APPROVED ? "Document approved successfully." : "Document rejected.";
        return ResponseEntity.ok(ApiResponseDTO.success(message, response));
    }
}


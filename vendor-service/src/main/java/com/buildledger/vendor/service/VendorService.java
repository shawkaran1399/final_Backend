package com.buildledger.vendor.service;

import com.buildledger.vendor.dto.request.CreateVendorRequestDTO;
import com.buildledger.vendor.dto.request.UpdateVendorRequestDTO;
import com.buildledger.vendor.dto.response.VendorDocumentResponseDTO;
import com.buildledger.vendor.dto.response.VendorResponseDTO;
import com.buildledger.vendor.enums.DocumentType;
import com.buildledger.vendor.enums.VendorStatus;
import com.buildledger.vendor.enums.VerificationStatus;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VendorService {
    VendorResponseDTO registerVendor(CreateVendorRequestDTO request);
    VendorResponseDTO getVendorById(Long vendorId);
    VendorResponseDTO getVendorByUsername(String username);
    List<VendorResponseDTO> getAllVendors();
    List<VendorResponseDTO> getVendorsByStatus(VendorStatus status);
    VendorResponseDTO updateVendor(Long vendorId, UpdateVendorRequestDTO request);
    void deleteVendor(Long vendorId);

    VendorDocumentResponseDTO uploadDocument(Long vendorId, MultipartFile file,
                                              DocumentType docType, String remarks, String uploaderUsername);
    VendorDocumentResponseDTO getVendorDocument(Long vendorId);
    List<VendorDocumentResponseDTO> getDocumentsByStatus(VerificationStatus status);
    Resource downloadDocument(Long documentId);
    String getDocumentFileUrl(Long documentId);
    VendorDocumentResponseDTO replaceDocument(Long vendorId, MultipartFile file,
                                               DocumentType docType, String remarks);
    VendorDocumentResponseDTO reviewDocument(Long documentId, VerificationStatus status,
                                              String reviewRemarks, String reviewerUsername);
}


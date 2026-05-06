package com.buildledger.vendor.service.impl;

import com.buildledger.vendor.event.NotificationEvent;
import com.buildledger.vendor.event.NotificationProducer;
import com.buildledger.vendor.dto.request.CreateVendorRequestDTO;
import com.buildledger.vendor.dto.request.UpdateVendorRequestDTO;
import com.buildledger.vendor.dto.response.ApiResponseDTO;
import com.buildledger.vendor.dto.response.VendorDocumentResponseDTO;
import com.buildledger.vendor.dto.response.VendorResponseDTO;
import com.buildledger.vendor.entity.Vendor;
import com.buildledger.vendor.entity.VendorDocument;
import com.buildledger.vendor.enums.DocumentType;
import com.buildledger.vendor.enums.VendorStatus;
import com.buildledger.vendor.enums.VerificationStatus;
import com.buildledger.vendor.exception.BadRequestException;
import com.buildledger.vendor.exception.DuplicateResourceException;
import com.buildledger.vendor.exception.ResourceNotFoundException;
import com.buildledger.vendor.feign.IamServiceClient;
import com.buildledger.vendor.repository.VendorDocumentRepository;
import com.buildledger.vendor.repository.VendorRepository;
import com.buildledger.vendor.service.VendorService;
import com.buildledger.vendor.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VendorServiceImpl implements VendorService {

    private final VendorRepository vendorRepository;
    private final VendorDocumentRepository vendorDocumentRepository;
    private final LocalFileStorageService fileStorageService;
    private final IamServiceClient iamServiceClient;
    private final NotificationProducer notificationProducer;

    @Value("${app.document.max-size-mb:10}")
    private long maxFileSizeMb;

    // ── Registration ──────────────────────────────────────────────────────────

    @Override
    public VendorResponseDTO registerVendor(CreateVendorRequestDTO request) {
        log.info("Vendor self-registration: {}", request.getName());
        if (vendorRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Vendor email already registered: " + request.getEmail());
        }
        if (vendorRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        String encodedPassword = new BCryptPasswordEncoder(12).encode(request.getPassword());
        Vendor vendor = Vendor.builder()
                .name(request.getName())
                .contactInfo(request.getContactInfo())
                .email(request.getEmail())
                .phone(request.getPhone())
                .category(request.getCategory())
                .address(request.getAddress())
                .username(request.getUsername())
                .passwordHash(encodedPassword)
                .build();
        VendorResponseDTO result = mapToResponse(vendorRepository.save(vendor));

        notificationProducer.send("vendor-events", NotificationEvent.builder()
                .recipientEmail(request.getEmail())
                .recipientName(request.getName())
                .type("VENDOR_REGISTERED")
                .subject("Welcome to BuildLedger — Registration Received")
                .message("Dear " + request.getName() + ", your vendor registration has been received successfully. "
                        + "Please upload your documents to proceed with verification.")
                .referenceId(String.valueOf(result.getVendorId()))
                .referenceType("VENDOR")
                .build());

        return result;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public VendorResponseDTO getVendorById(Long vendorId) {
        return mapToResponse(findVendorById(vendorId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponseDTO> getAllVendors() {
        return vendorRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorResponseDTO> getVendorsByStatus(VendorStatus status) {
        return vendorRepository.findByStatus(status).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ── Update / Delete ───────────────────────────────────────────────────────

    @Override
    public VendorResponseDTO updateVendor(Long vendorId, UpdateVendorRequestDTO request) {
        Vendor vendor = findVendorById(vendorId);
        if (vendor.getStatus() != VendorStatus.ACTIVE) {
            throw new BadRequestException(
                    "Only ACTIVE vendors can update their profile. Current status: " + vendor.getStatus());
        }
        if (request.getName() != null) vendor.setName(request.getName());
        if (request.getContactInfo() != null) vendor.setContactInfo(request.getContactInfo());
        if (request.getPhone() != null) vendor.setPhone(request.getPhone());
        if (request.getCategory() != null) vendor.setCategory(request.getCategory());
        if (request.getAddress() != null) vendor.setAddress(request.getAddress());
        if (request.getEmail() != null) {
            if (!request.getEmail().equalsIgnoreCase(vendor.getEmail())
                    && vendorRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already registered: " + request.getEmail());
            }
            vendor.setEmail(request.getEmail());
        }
        VendorResponseDTO result = mapToResponse(vendorRepository.save(vendor));

        notificationProducer.send("vendor-events", NotificationEvent.builder()
                .recipientEmail(vendor.getEmail())
                .recipientName(vendor.getName())
                .type("VENDOR_PROFILE_UPDATED")
                .subject("Your vendor profile has been updated")
                .message("Dear " + vendor.getName() + ", your vendor profile has been updated successfully.")
                .referenceId(String.valueOf(vendor.getVendorId()))
                .referenceType("VENDOR")
                .build());

        return result;
    }

    @Override
    public void deleteVendor(Long vendorId) {
        Vendor vendor = findVendorById(vendorId);
        vendorDocumentRepository.findByVendorVendorId(vendorId)
                .ifPresent(doc -> fileStorageService.delete(doc.getFileUri()));
        vendorRepository.delete(vendor);
        log.info("Vendor deleted: id={}", vendorId);

        notificationProducer.send("vendor-events", NotificationEvent.builder()
                .recipientEmail(vendor.getEmail())
                .recipientName(vendor.getName())
                .type("VENDOR_DELETED")
                .subject("Your vendor account has been deleted")
                .message("Dear " + vendor.getName() + ", your vendor account has been permanently "
                        + "deleted from BuildLedger. Please contact support if this was unexpected.")
                .referenceId(String.valueOf(vendorId))
                .referenceType("VENDOR")
                .build());
    }

    // ── Document Upload ───────────────────────────────────────────────────────

    @Override
    public VendorDocumentResponseDTO uploadDocument(Long vendorId, MultipartFile file,
                                                    DocumentType docType, String remarks,
                                                    String uploaderUsername) {
        log.info("Document upload: vendorId={}, docType={}, uploader={}", vendorId, docType, uploaderUsername);
        Vendor vendor = findVendorById(vendorId);

        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new BadRequestException(
                    "Document upload is not allowed. Vendor status is " + vendor.getStatus() +
                            ". Only PENDING vendors can upload documents.");
        }
        if (vendorDocumentRepository.findByVendorVendorId(vendorId).isPresent()) {
            throw new BadRequestException(
                    "A document has already been uploaded for this vendor. Only one document per vendor is allowed.");
        }

        validateFile(file);
        String fileUri = fileStorageService.store(file, vendorId);

        VendorDocument document = VendorDocument.builder()
                .vendor(vendor)
                .docType(docType)
                .fileUri(fileUri)
                .uploadedDate(LocalDate.now())
                .verificationStatus(VerificationStatus.PENDING)
                .remarks(remarks)
                .build();

        VendorDocument saved = vendorDocumentRepository.save(document);
        log.info("Document saved: id={}", saved.getDocumentId());

        notificationProducer.send("vendor-events", NotificationEvent.builder()
                .recipientEmail(vendor.getEmail())
                .recipientName(vendor.getName())
                .type("VENDOR_DOCUMENT_PENDING")
                .subject("Document uploaded — pending review")
                .message("Dear " + vendor.getName() + ", your document has been uploaded successfully and is now "
                        + "pending review by our team. You will be notified once the review is complete.")
                .referenceId(String.valueOf(vendor.getVendorId()))
                .referenceType("VENDOR")
                .build());

        return mapDocumentToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorDocumentResponseDTO getVendorDocument(Long vendorId) {
        findVendorById(vendorId);
        VendorDocument doc = vendorDocumentRepository.findByVendorVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDocument", "vendorId", vendorId));
        return mapDocumentToResponse(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorDocumentResponseDTO> getDocumentsByStatus(VerificationStatus status) {
        return vendorDocumentRepository.findByVerificationStatus(status).stream()
                .map(this::mapDocumentToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(Long documentId) {
        VendorDocument document = findDocumentById(documentId);
        return fileStorageService.load(document.getFileUri());
    }

    @Override
    @Transactional(readOnly = true)
    public String getDocumentFileUrl(Long documentId) {
        VendorDocument document = findDocumentById(documentId);
        return document.getFileUri();
    }

    @Override
    public VendorDocumentResponseDTO replaceDocument(Long vendorId, MultipartFile file,
                                                     DocumentType docType, String remarks) {
        log.info("Document replace: vendorId={}", vendorId);
        Vendor vendor = findVendorById(vendorId);

        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new BadRequestException(
                    "Document replacement is not allowed. Only PENDING vendors can replace their document. " +
                            "Current status: " + vendor.getStatus());
        }

        VendorDocument existing = vendorDocumentRepository.findByVendorVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDocument", "vendorId", vendorId));

        if (existing.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException(
                    "Cannot replace a document that has already been " + existing.getVerificationStatus() + ".");
        }

        validateFile(file);
        fileStorageService.delete(existing.getFileUri());
        String newFileUri = fileStorageService.store(file, vendorId);

        existing.setDocType(docType);
        existing.setFileUri(newFileUri);
        existing.setUploadedDate(LocalDate.now());
        existing.setVerificationStatus(VerificationStatus.PENDING);
        existing.setRemarks(remarks);

        VendorDocumentResponseDTO result = mapDocumentToResponse(vendorDocumentRepository.save(existing));

        notificationProducer.send("vendor-events", NotificationEvent.builder()
                .recipientEmail(vendor.getEmail())
                .recipientName(vendor.getName())
                .type("VENDOR_DOCUMENT_REPLACED")
                .subject("Document replaced — pending review")
                .message("Dear " + vendor.getName() + ", your document has been replaced successfully "
                        + "and is now pending review again. You will be notified once reviewed.")
                .referenceId(String.valueOf(vendor.getVendorId()))
                .referenceType("VENDOR")
                .build());

        return result;
    }

    @Override
    public VendorDocumentResponseDTO reviewDocument(Long documentId, VerificationStatus status,
                                                    String reviewRemarks, String reviewerUsername) {
        log.info("Document review: id={}, newStatus={}, reviewer={}", documentId, status, reviewerUsername);

        if (status == VerificationStatus.PENDING) {
            throw new BadRequestException("Review result must be APPROVED or REJECTED, not PENDING.");
        }

        VendorDocument document = findDocumentById(documentId);
        if (document.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException(
                    "Document is already " + document.getVerificationStatus() +
                            " and cannot be reviewed again. The decision is final.");
        }

        document.setVerificationStatus(status);
        document.setReviewedBy(reviewerUsername);
        document.setReviewedAt(LocalDateTime.now());
        document.setReviewRemarks(reviewRemarks);
        vendorDocumentRepository.save(document);

        if (status == VerificationStatus.APPROVED) {
            notificationProducer.send("vendor-events", NotificationEvent.builder()
                    .recipientEmail(document.getVendor().getEmail())
                    .recipientName(document.getVendor().getName())
                    .type("VENDOR_DOCUMENT_APPROVED")
                    .subject("Your document has been approved")
                    .message("Dear " + document.getVendor().getName() + ", your submitted document "
                            + "has been APPROVED by " + reviewerUsername
                            + ". Your account will be activated shortly.")
                    .referenceId(String.valueOf(document.getVendor().getVendorId()))
                    .referenceType("VENDOR")
                    .build());
        }

        if (status == VerificationStatus.REJECTED) {
            notificationProducer.send("vendor-events", NotificationEvent.builder()
                    .recipientEmail(document.getVendor().getEmail())
                    .recipientName(document.getVendor().getName())
                    .type("VENDOR_DOCUMENT_REJECTED")
                    .subject("Your document has been rejected")
                    .message("Dear " + document.getVendor().getName() + ", your submitted document "
                            + "has been REJECTED by " + reviewerUsername
                            + ". Reason: " + reviewRemarks
                            + ". Your account has been suspended.")
                    .referenceId(String.valueOf(document.getVendor().getVendorId()))
                    .referenceType("VENDOR")
                    .build());
        }

        autoUpdateVendorStatus(document.getVendor());
        return mapDocumentToResponse(document);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void autoUpdateVendorStatus(Vendor vendor) {
        if (vendor.getStatus() != VendorStatus.PENDING) return;

        vendorDocumentRepository.findByVendorVendorId(vendor.getVendorId()).ifPresent(doc -> {
            if (doc.getVerificationStatus() == VerificationStatus.APPROVED) {
                vendor.setStatus(VendorStatus.ACTIVE);
                vendorRepository.save(vendor);
                log.info("Vendor {} auto-promoted to ACTIVE", vendor.getVendorId());
                createVendorUserAccount(vendor);

                notificationProducer.send("vendor-events", NotificationEvent.builder()
                        .recipientEmail(vendor.getEmail())
                        .recipientName(vendor.getName())
                        .type("VENDOR_ACTIVATED")
                        .subject("Your vendor account is now ACTIVE!")
                        .message("Congratulations " + vendor.getName() + "! Your vendor account has been "
                                + "activated. You can now log in and start working on contracts.")
                        .referenceId(String.valueOf(vendor.getVendorId()))
                        .referenceType("VENDOR")
                        .build());

            } else if (doc.getVerificationStatus() == VerificationStatus.REJECTED) {
                vendor.setStatus(VendorStatus.SUSPENDED);
                vendor.setUserId(null);
                vendorRepository.save(vendor);
                log.info("Vendor {} SUSPENDED due to rejected document", vendor.getVendorId());

                notificationProducer.send("vendor-events", NotificationEvent.builder()
                        .recipientEmail(vendor.getEmail())
                        .recipientName(vendor.getName())
                        .type("VENDOR_SUSPENDED")
                        .subject("Your vendor account has been suspended")
                        .message("Dear " + vendor.getName() + ", your vendor account has been suspended due to "
                                + "rejected document verification. Please contact support for assistance.")
                        .referenceId(String.valueOf(vendor.getVendorId()))
                        .referenceType("VENDOR")
                        .build());
            }
        });
    }

    private void createVendorUserAccount(Vendor vendor) {
        if (vendor.getUserId() != null) {
            log.info("Vendor {} already has userId={}", vendor.getVendorId(), vendor.getUserId());
            return;
        }

        String username = vendor.getUsername();
        String encodedPassword = vendor.getPasswordHash();

        if (username == null || encodedPassword == null) {
            log.error("Vendor {} has no stored credentials – cannot create IAM account", vendor.getVendorId());
            return;
        }

        log.info("Creating IAM user account for vendor {} with username '{}'", vendor.getVendorId(), username);

        try {
            ApiResponseDTO<?> response = iamServiceClient.createVendorUser(
                    username, encodedPassword, vendor.getName(), vendor.getEmail(), vendor.getPhone());

            if (response.isSuccess() && response.getData() != null) {
                Object data = response.getData();
                if (data instanceof java.util.Map) {
                    Object userId = ((java.util.Map<?, ?>) data).get("userId");
                    if (userId != null) {
                        vendor.setUserId(Long.parseLong(userId.toString()));
                        vendorRepository.save(vendor);
                        log.info("Vendor {} IAM account created: userId={}", vendor.getVendorId(), userId);
                    }
                }
            } else {
                log.error("Failed to create IAM account for vendor {}: {}", vendor.getVendorId(), response.getMessage());
            }
        } catch (Exception e) {
            log.error("Error calling IAM service for vendor {}: {}", vendor.getVendorId(), e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided. Please attach a PDF file.");
        }
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf");
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Only PDF files are accepted. Got: " + originalName);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new BadRequestException("Invalid content type: " + contentType + ". Only application/pdf is allowed.");
        }
        long maxBytes = maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File size " + (file.getSize() / (1024 * 1024)) +
                    " MB exceeds the allowed limit of " + maxFileSizeMb + " MB.");
        }
    }

    private Vendor findVendorById(Long vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", "id", vendorId));
    }

    private VendorDocument findDocumentById(Long documentId) {
        return vendorDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDocument", "id", documentId));
    }

    private VendorResponseDTO mapToResponse(Vendor v) {
        return VendorResponseDTO.builder()
                .vendorId(v.getVendorId()).name(v.getName()).contactInfo(v.getContactInfo())
                .email(v.getEmail()).phone(v.getPhone()).category(v.getCategory()).address(v.getAddress())
                .username(v.getUsername())
                .status(v.getStatus())
                .userId(v.getStatus() == VendorStatus.ACTIVE ? v.getUserId() : null)
                .createdAt(v.getCreatedAt()).updatedAt(v.getUpdatedAt()).build();
    }

    private VendorDocumentResponseDTO mapDocumentToResponse(VendorDocument doc) {
        String fileUri = doc.getFileUri();
        String displayName = fileUri != null ? fileUri.replaceAll(".*[\\\\/]", "") : "unknown.pdf";
        return VendorDocumentResponseDTO.builder()
                .documentId(doc.getDocumentId()).vendorId(doc.getVendor().getVendorId())
                .vendorName(doc.getVendor().getName()).docType(doc.getDocType())
                .fileUri(displayName).uploadedDate(doc.getUploadedDate())
                .verificationStatus(doc.getVerificationStatus()).remarks(doc.getRemarks())
                .reviewedBy(doc.getReviewedBy()).reviewedAt(doc.getReviewedAt())
                .reviewRemarks(doc.getReviewRemarks()).createdAt(doc.getCreatedAt()).build();
    }
}
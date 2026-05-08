package com.buildledger.compliance.service.impl;

import com.buildledger.compliance.event.NotificationEvent;
import com.buildledger.compliance.event.NotificationProducer;
import com.buildledger.compliance.dto.request.ComplianceRecordRequestDTO;
import com.buildledger.compliance.dto.response.*;
import com.buildledger.compliance.entity.ComplianceRecord;
import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import com.buildledger.compliance.exception.BadRequestException;
import com.buildledger.compliance.exception.ResourceNotFoundException;
import com.buildledger.compliance.exception.ServiceUnavailableException;
import com.buildledger.compliance.feign.ContractServiceClient;
import com.buildledger.compliance.feign.ContractServiceFallback;
import com.buildledger.compliance.feign.DeliveryServiceClient;
import com.buildledger.compliance.feign.DeliveryServiceFallback;
import com.buildledger.compliance.repository.ComplianceRecordRepository;
import com.buildledger.compliance.service.AuditService;
import com.buildledger.compliance.service.ComplianceService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceServiceImpl implements ComplianceService {

    private final ComplianceRecordRepository complianceRecordRepository;
    private final ContractServiceClient contractServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final NotificationProducer notificationProducer;
    private final AuditService auditService;

    @Override
    public ComplianceRecordResponseDTO createComplianceRecord(ComplianceRecordRequestDTO request,
                                                              String reviewerUsername) {
        log.info("Creating compliance record type={} referenceId={}", request.getType(), request.getContractId());
        validateReferenceEntityStatus(request.getContractId(), request.getType());

        ComplianceRecord record = ComplianceRecord.builder()
                .contractId(request.getContractId())
                .type(request.getType())
                .date(request.getDate())
                .notes(request.getNotes())
                .status(ComplianceStatus.PENDING)
                .reviewedBy(reviewerUsername)
                .build();

        ComplianceRecordResponseDTO result = mapToResponse(complianceRecordRepository.save(record));

        notificationProducer.send("compliance-events", NotificationEvent.builder()
                .recipientEmail("")
                .recipientName(reviewerUsername)
                .type("COMPLIANCE_RECORD_CREATED")
                .subject("New compliance record created for contract #" + request.getContractId())
                .message("A new compliance record has been created for contract #" + request.getContractId()
                        + ". Type: " + request.getType()
                        + ", Due date: " + request.getDate()
                        + ". Status: PENDING.")
                .referenceId(String.valueOf(result.getComplianceId()))
                .referenceType("COMPLIANCE")
                .build());

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ComplianceRecordResponseDTO getComplianceRecordById(Long id) {
        return mapToResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRecordResponseDTO> getAllComplianceRecords() {
        return complianceRecordRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRecordResponseDTO> getByContract(Long referenceId) {
        return complianceRecordRepository.findByContractId(referenceId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceRecordResponseDTO> getByStatus(ComplianceStatus status) {
        return complianceRecordRepository.findByStatus(status).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public ComplianceRecordResponseDTO updateComplianceRecord(Long id, ComplianceRecordRequestDTO request,
                                                              String reviewerUsername) {
        ComplianceRecord record = findById(id);

        if (record.getStatus() != ComplianceStatus.PENDING) {
            throw new BadRequestException(
                    "Compliance record can only be updated in PENDING status. Current status: " + record.getStatus());
        }
        if (request.getContractId() != null && !request.getContractId().equals(record.getContractId())) {
            record.setContractId(request.getContractId());
        }
        if (request.getType() != null) record.setType(request.getType());
        if (request.getDate() != null) record.setDate(request.getDate());
        if (request.getNotes() != null) record.setNotes(request.getNotes());
        record.setReviewedBy(reviewerUsername);

        ComplianceRecordResponseDTO result = mapToResponse(complianceRecordRepository.save(record));

        notificationProducer.send("compliance-events", NotificationEvent.builder()
                .recipientEmail("")
                .recipientName(reviewerUsername)
                .type("COMPLIANCE_RECORD_UPDATED")
                .subject("Compliance record #" + id + " has been updated")
                .message("Compliance record #" + id + " for contract #" + record.getContractId()
                        + " has been updated by " + reviewerUsername + ".")
                .referenceId(String.valueOf(id))
                .referenceType("COMPLIANCE")
                .build());

        return result;
    }

    @Override
    public ComplianceRecordResponseDTO updateComplianceStatus(Long id, ComplianceStatus newStatus,
                                                              String reviewerUsername, String remarks) {
        ComplianceRecord record = findById(id);
        ComplianceStatus current = record.getStatus();

        if (newStatus == ComplianceStatus.FAILED) {
            if (remarks == null || remarks.isBlank()) {
                throw new BadRequestException(
                        "Remarks are required when marking a compliance record as FAILED. " +
                        "Please provide a reason explaining why the check failed.");
            }
            record.setNotes(remarks);
        }

        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException(
                    "Invalid compliance status transition from " + current + " to " + newStatus +
                            ". Lifecycle: PENDING→UNDER_REVIEW, UNDER_REVIEW→PASSED|FAILED|WAIVED, FAILED→PENDING.");
        }

        if (current == ComplianceStatus.FAILED && newStatus == ComplianceStatus.PENDING) {
            int retries = record.getRetryCount() + 1;
            if (retries >= 3) {
                throw new BadRequestException(
                    "Compliance record #" + id + " has exceeded the maximum retry limit (3). " +
                    "Manual escalation required.");
            }
            record.setRetryCount(retries);
        }

        record.setStatus(newStatus);
        record.setReviewedBy(reviewerUsername);
        ComplianceRecordResponseDTO result = mapToResponse(complianceRecordRepository.save(record));

        // Automatically record the status change in the immutable audit log (fail-open)
        try {
            auditService.createAuditEntry(
                record.getComplianceId(),
                newStatus.name(),
                reviewerUsername,
                record.getNotes()
            );
        } catch (Exception e) {
            log.warn("Audit log creation failed for compliance record #{} — compliance update was still saved: {}",
                record.getComplianceId(), e.getMessage());
        }

        // Fires on EVERY status change
        notificationProducer.send("compliance-events", NotificationEvent.builder()
                .recipientEmail("")
                .recipientName(reviewerUsername)
                .type("COMPLIANCE_STATUS_CHANGED")
                .subject("Compliance record #" + record.getComplianceId() + " status changed")
                .message("Compliance record #" + record.getComplianceId()
                        + " for contract #" + record.getContractId()
                        + " status changed from " + current + " to " + newStatus
                        + ". Reviewed by: " + reviewerUsername)
                .referenceId(String.valueOf(record.getComplianceId()))
                .referenceType("COMPLIANCE")
                .build());

        // Fires based on specific status
        if (newStatus == ComplianceStatus.UNDER_REVIEW) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_REVIEW_STARTED")
                    .subject("Compliance review started for record #" + id)
                    .message("Compliance record #" + id + " for contract #" + record.getContractId()
                            + " is now UNDER REVIEW by " + reviewerUsername + ".")
                    .referenceId(String.valueOf(id))
                    .referenceType("COMPLIANCE")
                    .build());

        } else if (newStatus == ComplianceStatus.PASSED) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_CHECK_PASSED")
                    .subject("Compliance check PASSED for record #" + id)
                    .message("Compliance record #" + id + " for contract #" + record.getContractId()
                            + " has PASSED the compliance check. Reviewed by: " + reviewerUsername)
                    .referenceId(String.valueOf(id))
                    .referenceType("COMPLIANCE")
                    .build());

        } else if (newStatus == ComplianceStatus.FAILED) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_CHECK_FAILED")
                    .subject("Compliance check FAILED for record #" + id)
                    .message("Compliance record #" + id + " for contract #" + record.getContractId()
                            + " has FAILED the compliance check. Immediate action required. "
                            + "Reviewed by: " + reviewerUsername)
                    .referenceId(String.valueOf(id))
                    .referenceType("COMPLIANCE")
                    .build());

        } else if (newStatus == ComplianceStatus.WAIVED) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_WAIVED")
                    .subject("Compliance check WAIVED for record #" + id)
                    .message("Compliance record #" + id + " for contract #" + record.getContractId()
                            + " has been WAIVED by " + reviewerUsername + ".")
                    .referenceId(String.valueOf(id))
                    .referenceType("COMPLIANCE")
                    .build());

        } else if (newStatus == ComplianceStatus.PENDING) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_REINITIATED")
                    .subject("Compliance record #" + id + " has been reinitiated")
                    .message("Compliance record #" + id + " for contract #" + record.getContractId()
                            + " has been reinitiated back to PENDING for retry. "
                            + "Reviewed by: " + reviewerUsername)
                    .referenceId(String.valueOf(id))
                    .referenceType("COMPLIANCE")
                    .build());
        }

        return result;
    }

    @Override
    public void deleteComplianceRecord(Long id) {
        ComplianceRecord record = findById(id);
        if (record.getStatus() != ComplianceStatus.PENDING) {
            throw new BadRequestException(
                    "Only PENDING compliance records can be deleted. Current status: " + record.getStatus());
        }
        complianceRecordRepository.delete(record);
        log.info("Compliance record deleted: id={}", id);

        notificationProducer.send("compliance-events", NotificationEvent.builder()
                .recipientEmail("")
                .recipientName("Admin")
                .type("COMPLIANCE_RECORD_DELETED")
                .subject("Compliance record #" + id + " has been deleted")
                .message("Compliance record #" + id + " for contract #" + record.getContractId()
                        + " has been permanently deleted.")
                .referenceId(String.valueOf(id))
                .referenceType("COMPLIANCE")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompliancePassed(Long referenceId, ComplianceType type) {
        return complianceRecordRepository.existsByContractIdAndTypeAndStatusIn(
                referenceId, type, List.of(ComplianceStatus.PASSED, ComplianceStatus.WAIVED));
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private void validateReferenceEntityStatus(Long referenceId, ComplianceType type) {
        switch (type) {
            case DELIVERY_CHECK -> {
                ApiResponseDTO<Map<String, Object>> res;
                try { res = deliveryServiceClient.getDeliveryById(referenceId); }
                catch (FeignException.NotFound e) { throw new ResourceNotFoundException("Delivery", "id", referenceId); }
                catch (Exception e) { throw new ServiceUnavailableException("Delivery Service is currently unavailable."); }
                if (DeliveryServiceFallback.MARKER.equals(res.getMessage()))
                    throw new ServiceUnavailableException("Delivery Service is currently unavailable.");
                if (!res.isSuccess() || res.getData() == null)
                    throw new ResourceNotFoundException("Delivery", "id", referenceId);
                String status = (String) res.getData().get("status");
                if (!"MARKED_DELIVERED".equals(status))
                    throw new BadRequestException(
                        "A DELIVERY_CHECK compliance record can only be created for deliveries in MARKED_DELIVERED status. " +
                        "Delivery #" + referenceId + " has status: " + status);
            }
            case SERVICE_CHECK -> {
                ApiResponseDTO<Map<String, Object>> res;
                try { res = deliveryServiceClient.getServiceById(referenceId); }
                catch (FeignException.NotFound e) { throw new ResourceNotFoundException("ServiceRecord", "id", referenceId); }
                catch (Exception e) { throw new ServiceUnavailableException("Delivery Service is currently unavailable."); }
                if (DeliveryServiceFallback.MARKER.equals(res.getMessage()))
                    throw new ServiceUnavailableException("Delivery Service is currently unavailable.");
                if (!res.isSuccess() || res.getData() == null)
                    throw new ResourceNotFoundException("ServiceRecord", "id", referenceId);
                String status = (String) res.getData().get("status");
                if (!"COMPLETED".equals(status))
                    throw new BadRequestException(
                        "A SERVICE_CHECK compliance record can only be created for services in COMPLETED status. " +
                        "Service #" + referenceId + " has status: " + status);
            }
        }
    }

    private void validateContract(Long contractId) {
        ApiResponseDTO<Map<String, Object>> res;
        try {
            res = contractServiceClient.getContractById(contractId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }
        if (ContractServiceFallback.MARKER.equals(res.getMessage())) {
            throw new ServiceUnavailableException("Contract Service is currently unavailable. Please try again later.");
        }
        if (!res.isSuccess() || res.getData() == null) {
            throw new ResourceNotFoundException("Contract", "id", contractId);
        }
        String contractStatus = (String) res.getData().get("status");
        if (!"ACTIVE".equals(contractStatus) && !"COMPLETED".equals(contractStatus)) {
            throw new BadRequestException(
                "Compliance records can only be created for ACTIVE or COMPLETED contracts. " +
                "Contract #" + contractId + " has status: " + contractStatus);
        }
    }

    private ComplianceRecord findById(Long id) {
        return complianceRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceRecord", "id", id));
    }

    private ComplianceRecordResponseDTO mapToResponse(ComplianceRecord r) {
        return ComplianceRecordResponseDTO.builder()
                .complianceId(r.getComplianceId())
                .contractId(r.getContractId())
                .type(r.getType())
                .date(r.getDate())
                .notes(r.getNotes())
                .status(r.getStatus())
                .reviewedBy(r.getReviewedBy())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
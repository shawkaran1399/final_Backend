package com.buildledger.compliance.service.impl;

import com.buildledger.compliance.event.NotificationEvent;
import com.buildledger.compliance.event.NotificationProducer;
import com.buildledger.compliance.dto.request.ComplianceRecordRequestDTO;
import com.buildledger.compliance.dto.response.*;
import com.buildledger.compliance.entity.ComplianceRecord;
import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.exception.BadRequestException;
import com.buildledger.compliance.exception.ResourceNotFoundException;
import com.buildledger.compliance.exception.ServiceUnavailableException;
import com.buildledger.compliance.feign.ContractServiceClient;
import com.buildledger.compliance.feign.ContractServiceFallback;
import com.buildledger.compliance.repository.ComplianceRecordRepository;
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
    private final NotificationProducer notificationProducer;

    @Override
    public ComplianceRecordResponseDTO createComplianceRecord(ComplianceRecordRequestDTO request,
                                                              String reviewerUsername) {
        log.info("Creating compliance record for contract {}", request.getContractId());
        validateContract(request.getContractId());

        ComplianceRecord record = ComplianceRecord.builder()
                .contractId(request.getContractId())
                .type(request.getType())
                .result(request.getResult())
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
    public List<ComplianceRecordResponseDTO> getByContract(Long contractId) {
        validateContract(contractId);
        return complianceRecordRepository.findByContractId(contractId).stream()
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
            validateContract(request.getContractId());
            record.setContractId(request.getContractId());
        }
        if (request.getType() != null) record.setType(request.getType());
        if (request.getResult() != null) record.setResult(request.getResult());
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
                                                              String reviewerUsername) {
        ComplianceRecord record = findById(id);
        ComplianceStatus current = record.getStatus();

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

    // ── Private Helpers ──────────────────────────────────────────────────────

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
                .result(r.getResult())
                .date(r.getDate())
                .notes(r.getNotes())
                .status(r.getStatus())
                .reviewedBy(r.getReviewedBy())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
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
    private final NotificationProducer notificationProducer;  // ADD THIS

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

        return mapToResponse(complianceRecordRepository.save(record));
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

        return mapToResponse(complianceRecordRepository.save(record));
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

// Fires only when PASSED
        if (newStatus == ComplianceStatus.PASSED) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_CHECK_PASSED")
                    .subject("Compliance check passed for contract #" + record.getContractId())
                    .message("Compliance record #" + record.getComplianceId()
                            + " for contract #" + record.getContractId()
                            + " has PASSED the compliance check. Reviewed by: " + reviewerUsername)
                    .referenceId(String.valueOf(record.getComplianceId()))
                    .referenceType("COMPLIANCE")
                    .build());
        }

// Fires only when FAILED
        if (newStatus == ComplianceStatus.FAILED) {
            notificationProducer.send("compliance-events", NotificationEvent.builder()
                    .recipientEmail("")
                    .recipientName(reviewerUsername)
                    .type("COMPLIANCE_CHECK_FAILED")
                    .subject("Compliance check FAILED for contract #" + record.getContractId())
                    .message("Compliance record #" + record.getComplianceId()
                            + " for contract #" + record.getContractId()
                            + " has FAILED the compliance check. Immediate action required. "
                            + "Reviewed by: " + reviewerUsername)
                    .referenceId(String.valueOf(record.getComplianceId()))
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


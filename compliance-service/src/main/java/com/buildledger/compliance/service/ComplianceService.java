package com.buildledger.compliance.service;

import com.buildledger.compliance.dto.request.ComplianceRecordRequestDTO;
import com.buildledger.compliance.dto.response.ComplianceRecordResponseDTO;
import com.buildledger.compliance.enums.ComplianceStatus;
import com.buildledger.compliance.enums.ComplianceType;
import java.util.List;

public interface ComplianceService {
    ComplianceRecordResponseDTO createComplianceRecord(ComplianceRecordRequestDTO request, String reviewerUsername);
    ComplianceRecordResponseDTO getComplianceRecordById(Long id);
    List<ComplianceRecordResponseDTO> getAllComplianceRecords();
    List<ComplianceRecordResponseDTO> getByContract(Long contractId);
    List<ComplianceRecordResponseDTO> getByStatus(ComplianceStatus status);
    ComplianceRecordResponseDTO updateComplianceRecord(Long id, ComplianceRecordRequestDTO request, String reviewerUsername);
    ComplianceRecordResponseDTO updateComplianceStatus(Long id, ComplianceStatus newStatus, String reviewerUsername, String remarks);
    void deleteComplianceRecord(Long id);
    boolean isCompliancePassed(Long referenceId, ComplianceType type);
}

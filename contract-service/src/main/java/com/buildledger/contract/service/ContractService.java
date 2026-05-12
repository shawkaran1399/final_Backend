package com.buildledger.contract.service;

import com.buildledger.contract.dto.request.ContractRequestDTO;
import com.buildledger.contract.dto.request.ContractTermRequestDTO;
import com.buildledger.contract.dto.response.BudgetSummaryDTO;
import com.buildledger.contract.dto.response.ContractResponseDTO;
import com.buildledger.contract.dto.response.ContractTermResponseDTO;
import com.buildledger.contract.enums.ContractStatus;
import java.util.List;

public interface ContractService {
    ContractResponseDTO createContract(ContractRequestDTO request);
    ContractResponseDTO getContractById(Long contractId);
    List<ContractResponseDTO> getAllContracts();
    List<ContractResponseDTO> getContractsByVendor(Long vendorId);
    List<ContractResponseDTO> getContractsByProject(Long projectId);
    List<ContractResponseDTO> getContractsByStatus(ContractStatus status);

    // Returns contracts for the logged-in PM's assigned projects
    List<ContractResponseDTO> getContractsByManagerUsername(String managerUsername);

    // Returns contracts assigned to the logged-in vendor (by username)
    // Used by DeliveryTracking so vendor can see their ACTIVE contracts in dropdown
    List<ContractResponseDTO> getContractsByVendorUsername(String vendorUsername);

    ContractResponseDTO updateContract(Long contractId, ContractRequestDTO request);
    ContractResponseDTO updateContractStatus(Long contractId, ContractStatus status);
    ContractResponseDTO vendorRespondToContract(Long contractId, String action, String remarks, Long vendorId);
    void deleteContract(Long contractId);

    ContractTermResponseDTO addContractTerm(Long contractId, ContractTermRequestDTO request);
    List<ContractTermResponseDTO> getContractTerms(Long contractId);
    ContractTermResponseDTO editContractTerm(Long termId, ContractTermRequestDTO request);
    void deleteContractTerm(Long termId);

    BudgetSummaryDTO getProjectBudgetSummary(Long projectId);
}
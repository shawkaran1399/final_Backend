package com.buildledger.contract.service;

import com.buildledger.contract.dto.request.ContractRequestDTO;
import com.buildledger.contract.dto.request.ContractTermRequestDTO;
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
    ContractResponseDTO updateContract(Long contractId, ContractRequestDTO request);
    ContractResponseDTO updateContractStatus(Long contractId, ContractStatus status);
    void deleteContract(Long contractId);
    
    ContractTermResponseDTO addContractTerm(Long contractId, ContractTermRequestDTO request);
    List<ContractTermResponseDTO> getContractTerms(Long contractId);
    ContractTermResponseDTO editContractTerm(Long termId, ContractTermRequestDTO request);
    void deleteContractTerm(Long termId);

    void propagateVendorNameChange(Long vendorId, String newName);
}


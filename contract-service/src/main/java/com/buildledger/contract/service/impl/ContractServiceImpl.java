package com.buildledger.contract.service.impl;

import com.buildledger.contract.dto.request.ContractRequestDTO;
import com.buildledger.contract.dto.request.ContractTermRequestDTO;
import com.buildledger.contract.dto.response.*;
import com.buildledger.contract.entity.Contract;
import com.buildledger.contract.entity.ContractTerm;
import com.buildledger.contract.entity.Project;
import com.buildledger.contract.enums.ContractStatus;
import com.buildledger.contract.exception.BadRequestException;
import com.buildledger.contract.exception.ResourceNotFoundException;
import com.buildledger.contract.exception.ServiceUnavailableException;
import com.buildledger.contract.feign.VendorServiceClient;
import com.buildledger.contract.feign.VendorServiceFallback;
import com.buildledger.contract.repository.ContractRepository;
import com.buildledger.contract.repository.ContractTermRepository;
import com.buildledger.contract.repository.ProjectRepository;
import com.buildledger.contract.service.ContractService;
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
public class ContractServiceImpl implements ContractService {

    private final ContractRepository contractRepository;
    private final ContractTermRepository contractTermRepository;
    private final ProjectRepository projectRepository;
    private final VendorServiceClient vendorServiceClient;

    public ContractResponseDTO createContract(ContractRequestDTO request) {
        log.info("Creating contract for vendor={}, project={}", request.getVendorId(), request.getProjectId());

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        // Validate vendor exists and is ACTIVE
        Map<String, Object> vendorData = fetchVendor(request.getVendorId());
        String vendorStatus = (String) vendorData.get("status");
        if (!"ACTIVE".equals(vendorStatus)) {
            throw new BadRequestException("Vendor is not ACTIVE. Current status: " + vendorStatus +
                ". Only ACTIVE vendors can be assigned to contracts.");
        }

        // Validate project exists and is ACTIVE
        Project project = findProjectById(request.getProjectId());
        if (project.getStatus() != com.buildledger.contract.enums.ProjectStatus.ACTIVE) {
            throw new BadRequestException(
                "Contracts can only be created for ACTIVE projects. Project '" + project.getName() +
                "' is currently " + project.getStatus() + ".");
        }

        // Validate new contract value does not push total beyond project budget
        java.math.BigDecimal existingContractTotal = contractRepository.findByProjectId(request.getProjectId())
            .stream()
            .map(com.buildledger.contract.entity.Contract::getValue)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (existingContractTotal.add(request.getValue()).compareTo(project.getBudget()) > 0) {
            throw new BadRequestException(
                "Contract value would exceed project budget. Budget: " + project.getBudget() +
                ", already contracted: " + existingContractTotal +
                ", requested: " + request.getValue() + ".");
        }

        Contract contract = Contract.builder()
            .vendorId(request.getVendorId())
            .vendorName((String) vendorData.get("name"))
            .projectId(request.getProjectId())
            .projectName(project.getName())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .value(request.getValue())
            .description(request.getDescription())
            .build();

        return mapToResponse(contractRepository.save(contract));
    }

    @Transactional(readOnly = true)
    public ContractResponseDTO getContractById(Long contractId) {
        return mapToResponse(findById(contractId));
    }

    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getAllContracts() {
        return contractRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getContractsByVendor(Long vendorId) {
        return contractRepository.findByVendorId(vendorId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getContractsByProject(Long projectId) {
        return contractRepository.findByProjectId(projectId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getContractsByStatus(ContractStatus status) {
        return contractRepository.findByStatus(status).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ContractResponseDTO updateContract(Long contractId, ContractRequestDTO request) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException(
                "Contract can only be edited in DRAFT status. Current status: " + contract.getStatus());
        }
        if (request.getStartDate() != null) contract.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) contract.setEndDate(request.getEndDate());
        if (request.getValue() != null) contract.setValue(request.getValue());
        if (request.getDescription() != null) contract.setDescription(request.getDescription());

        if (request.getVendorId() != null) {
            Map<String, Object> vendorData = fetchVendor(request.getVendorId());
            if (!"ACTIVE".equals(vendorData.get("status"))) {
                throw new BadRequestException("Vendor is not ACTIVE. Current status: " + vendorData.get("status"));
            }
            contract.setVendorId(request.getVendorId());
            contract.setVendorName((String) vendorData.get("name"));
        }
        if (request.getProjectId() != null) {
            Project project = findProjectById(request.getProjectId());
            contract.setProjectId(request.getProjectId());
            contract.setProjectName(project.getName());
        }
        return mapToResponse(contractRepository.save(contract));
    }

    public ContractResponseDTO updateContractStatus(Long contractId, ContractStatus newStatus) {
        Contract contract = findById(contractId);
        ContractStatus current = contract.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException(
                "Invalid status transition from " + current + " to " + newStatus +
                ". Allowed: DRAFT→ACTIVE, ACTIVE→COMPLETED|TERMINATED|EXPIRED.");
        }
        // A contract must have at least one term defined before it can go ACTIVE
        if (newStatus == ContractStatus.ACTIVE) {
            long termCount = contractTermRepository.findByContractContractIdOrderBySequenceNumberAsc(contractId).size();
            if (termCount == 0) {
                throw new BadRequestException(
                    "Contract cannot be activated without any terms. Please add at least one contract term first.");
            }
        }
        contract.setStatus(newStatus);
        return mapToResponse(contractRepository.save(contract));
    }

    public void deleteContract(Long contractId) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT contracts can be deleted. Current status: " + contract.getStatus());
        }
        contractRepository.delete(contract);
    }

    public ContractTermResponseDTO addContractTerm(Long contractId, ContractTermRequestDTO request) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("Terms can only be added to DRAFT contracts. Current status: " + contract.getStatus());
        }
        ContractTerm term = ContractTerm.builder()
            .contract(contract)
            .description(request.getDescription())
            .complianceFlag(request.getComplianceFlag() != null ? request.getComplianceFlag() : false)
            .sequenceNumber(request.getSequenceNumber())
            .build();
        return mapTermToResponse(contractTermRepository.save(term));
    }

    @Transactional(readOnly = true)
    public List<ContractTermResponseDTO> getContractTerms(Long contractId) {
        findById(contractId);
        return contractTermRepository.findByContractContractIdOrderBySequenceNumberAsc(contractId)
            .stream().map(this::mapTermToResponse).collect(Collectors.toList());
    }

    public ContractTermResponseDTO editContractTerm(Long termId, ContractTermRequestDTO request) {
        ContractTerm term = findTermById(termId);
        if (term.getContract().getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("Terms can only be edited on DRAFT contracts.");
        }
        if (request.getDescription() != null) term.setDescription(request.getDescription());
        if (request.getComplianceFlag() != null) term.setComplianceFlag(request.getComplianceFlag());
        if (request.getSequenceNumber() != null) term.setSequenceNumber(request.getSequenceNumber());
        return mapTermToResponse(contractTermRepository.save(term));
    }

    public void deleteContractTerm(Long termId) {
        ContractTerm term = findTermById(termId);
        if (term.getContract().getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("Terms can only be deleted on DRAFT contracts.");
        }
        contractTermRepository.delete(term);
    }

    public void propagateVendorNameChange(Long vendorId, String newName) {
        List<Contract> contracts = contractRepository.findByVendorId(vendorId);
        if (contracts.isEmpty()) return;
        contracts.forEach(c -> c.setVendorName(newName));
        contractRepository.saveAll(contracts);
        log.info("Propagated vendor name '{}' to {} contracts for vendorId={}", newName, contracts.size(), vendorId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> fetchVendor(Long vendorId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = vendorServiceClient.getVendorById(vendorId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Vendor", "id", vendorId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable. Please try again later.");
        }
        if (VendorServiceFallback.MARKER.equals(response.getMessage())) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable. Please try again later.");
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("Vendor", "id", vendorId);
        }
        return response.getData();
    }

    private Project findProjectById(Long projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private Contract findById(Long contractId) {
        return contractRepository.findById(contractId)
            .orElseThrow(() -> new ResourceNotFoundException("Contract", "id", contractId));
    }

    private ContractTerm findTermById(Long termId) {
        return contractTermRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("ContractTerm", "id", termId));
    }

    private ContractResponseDTO mapToResponse(Contract c) {
        return ContractResponseDTO.builder()
            .contractId(c.getContractId()).vendorId(c.getVendorId()).vendorName(c.getVendorName())
            .projectId(c.getProjectId()).projectName(c.getProjectName())
            .startDate(c.getStartDate()).endDate(c.getEndDate()).value(c.getValue())
            .status(c.getStatus()).description(c.getDescription())
            .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    private ContractTermResponseDTO mapTermToResponse(ContractTerm t) {
        return ContractTermResponseDTO.builder()
            .termId(t.getTermId()).contractId(t.getContract().getContractId())
            .description(t.getDescription()).complianceFlag(t.getComplianceFlag())
            .sequenceNumber(t.getSequenceNumber()).createdAt(t.getCreatedAt()).build();
    }
}


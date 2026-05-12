package com.buildledger.contract.service.impl;

import com.buildledger.contract.event.NotificationEvent;
import com.buildledger.contract.event.NotificationProducer;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContractServiceImpl implements ContractService {

    private final ContractRepository     contractRepository;
    private final ContractTermRepository contractTermRepository;
    private final ProjectRepository      projectRepository;
    private final VendorServiceClient    vendorServiceClient;
    private final NotificationProducer   notificationProducer;

    private static final Set<ContractStatus> TERMINAL_STATUSES = Set.of(
            ContractStatus.COMPLETED,
            ContractStatus.TERMINATED,
            ContractStatus.EXPIRED,
            ContractStatus.REJECTED
    );

    // ── Budget summary ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BudgetSummaryDTO getProjectBudgetSummary(Long projectId) {
        Project        project   = findProjectById(projectId);
        List<Contract> contracts = contractRepository.findByProjectId(projectId);

        List<Contract> activeContracts = contracts.stream()
                .filter(c -> !TERMINAL_STATUSES.contains(c.getStatus()))
                .collect(Collectors.toList());

        BigDecimal spent = activeContracts.stream()
                .map(c -> c.getValue() != null ? c.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBudget = project.getBudget() != null ? project.getBudget() : BigDecimal.ZERO;
        BigDecimal remaining   = totalBudget.subtract(spent);

        return BudgetSummaryDTO.builder()
                .projectId(projectId).projectName(project.getName())
                .totalBudget(totalBudget).spent(spent).remaining(remaining)
                .overBudget(remaining.compareTo(BigDecimal.ZERO) < 0)
                .activeContractCount(activeContracts.size())
                .build();
    }

    // ── Budget validation ─────────────────────────────────────────────────────

    private void validateBudget(Long projectId, BigDecimal value, Long excludeContractId) {
        if (value == null) return;
        List<Contract> contracts = contractRepository.findByProjectId(projectId);
        Project        project   = findProjectById(projectId);

        BigDecimal spent = contracts.stream()
                .filter(c -> !TERMINAL_STATUSES.contains(c.getStatus()))
                .filter(c -> !Objects.equals(c.getContractId(), excludeContractId))
                .map(c -> c.getValue() != null ? c.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = (project.getBudget() != null ? project.getBudget() : BigDecimal.ZERO)
                .subtract(spent);

        if (value.compareTo(remaining) > 0)
            throw new BadRequestException(
                    "Contract value (" + value + ") exceeds remaining project budget (" + remaining + "). "
                            + "Please reduce the contract value or ask admin to increase the project budget first.");
    }

    // ── Get contracts for the logged-in PM's projects only ────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getContractsByManagerUsername(String managerUsername) {
        log.info("Fetching contracts for manager username: {}", managerUsername);

        // Get all project IDs assigned to this PM
        List<Long> projectIds = projectRepository.findByManagerUsername(managerUsername)
                .stream().map(Project::getProjectId).collect(Collectors.toList());

        if (projectIds.isEmpty()) return List.of();

        // Return contracts only for those projects
        return projectIds.stream()
                .flatMap(pid -> contractRepository.findByProjectId(pid).stream())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Get contracts for the logged-in Vendor ────────────────────────────────

    /**
     * Returns contracts assigned to the logged-in vendor (by username).
     * Used by GET /contracts/vendor/my so the vendor can see their ACTIVE contracts
     * in the DeliveryTracking dropdown — without hitting the admin-only GET /contracts.
     *
     * Flow:
     *   1. vendorUsername (JWT principal) → vendorServiceClient.getVendorByUsername()
     *   2. Extract vendorId from response
     *   3. contractRepository.findByVendorId(vendorId)
     */
    @Override
    @Transactional(readOnly = true)
    public List<ContractResponseDTO> getContractsByVendorUsername(String vendorUsername) {
        log.info("Fetching contracts for vendor username: {}", vendorUsername);

        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = vendorServiceClient.getVendorByUsername(vendorUsername);
        } catch (FeignException.NotFound e) {
            log.warn("Vendor not found for username: {}", vendorUsername);
            return List.of();
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        }

        if (VendorServiceFallback.MARKER.equals(response.getMessage()))
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");

        if (!response.isSuccess() || response.getData() == null)
            return List.of();

        // Extract vendorId from vendor-service response
        Object vendorIdObj = response.getData().get("vendorId");
        if (vendorIdObj == null) return List.of();

        Long vendorId = vendorIdObj instanceof Integer
                ? ((Integer) vendorIdObj).longValue()
                : ((Number) vendorIdObj).longValue();

        return contractRepository.findByVendorId(vendorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public ContractResponseDTO createContract(ContractRequestDTO request) {
        log.info("Creating contract for vendor={}, project={}", request.getVendorId(), request.getProjectId());

        if (request.getEndDate().isBefore(request.getStartDate()))
            throw new BadRequestException("End date cannot be before start date");

        Map<String, Object> vendorData   = fetchVendor(request.getVendorId());
        String              vendorStatus = (String) vendorData.get("status");
        if (!"ACTIVE".equals(vendorStatus))
            throw new BadRequestException("Vendor is not ACTIVE. Current status: " + vendorStatus);

        Project project        = findProjectById(request.getProjectId());
        String  vendorName     = (String) vendorData.get("name");
        String  vendorUsername = String.valueOf(vendorData.getOrDefault("username", ""));

        validateBudget(request.getProjectId(), request.getValue(), null);

        Contract contract = Contract.builder()
                .vendorId(request.getVendorId()).vendorName(vendorName).vendorUsername(vendorUsername)
                .projectId(request.getProjectId()).projectName(project.getName())
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .value(request.getValue()).description(request.getDescription())
                .build();

        Contract saved = contractRepository.save(contract);

        if (request.getTerms() != null && !request.getTerms().isEmpty()) {
            int seq = 1;
            for (ContractTermRequestDTO termReq : request.getTerms()) {
                contractTermRepository.save(ContractTerm.builder()
                        .contract(saved).description(termReq.getDescription())
                        .complianceFlag(termReq.getComplianceFlag() != null ? termReq.getComplianceFlag() : false)
                        .sequenceNumber(seq++).build());
            }
        }

        ContractResponseDTO result = mapToResponse(saved);

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(vendorUsername).recipientName(vendorName)
                .type("CONTRACT_CREATED").subject("A new contract has been created for you")
                .message("Dear " + vendorName + ", a new contract for project '" + project.getName()
                        + "' has been created. Value: " + request.getValue()
                        + ". Dates: " + request.getStartDate() + " to " + request.getEndDate() + ". Status: DRAFT.")
                .referenceId(String.valueOf(result.getContractId())).referenceType("CONTRACT").build());

        return result;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

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

    // ── Update ────────────────────────────────────────────────────────────────

    public ContractResponseDTO updateContract(Long contractId, ContractRequestDTO request) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT)
            throw new BadRequestException("Contract can only be edited in DRAFT status. Current: " + contract.getStatus());

        if (request.getStartDate()   != null) contract.setStartDate(request.getStartDate());
        if (request.getEndDate()     != null) contract.setEndDate(request.getEndDate());
        if (request.getDescription() != null) contract.setDescription(request.getDescription());

        if (request.getVendorId() != null) {
            Map<String, Object> vendorData = fetchVendor(request.getVendorId());
            if (!"ACTIVE".equals(vendorData.get("status")))
                throw new BadRequestException("Vendor is not ACTIVE.");
            contract.setVendorId(request.getVendorId());
            contract.setVendorName((String) vendorData.get("name"));
            contract.setVendorUsername(String.valueOf(vendorData.getOrDefault("username", "")));
        }

        Long targetProjectId = request.getProjectId() != null ? request.getProjectId() : contract.getProjectId();
        if (request.getProjectId() != null) {
            Project project = findProjectById(request.getProjectId());
            contract.setProjectId(request.getProjectId());
            contract.setProjectName(project.getName());
        }

        if (request.getValue() != null) {
            validateBudget(targetProjectId, request.getValue(), contractId);
            contract.setValue(request.getValue());
        }

        ContractResponseDTO result = mapToResponse(contractRepository.save(contract));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(contract.getVendorUsername()).recipientName(contract.getVendorName())
                .type("CONTRACT_UPDATED").subject("Your contract has been updated")
                .message("Dear " + contract.getVendorName() + ", your contract for project '"
                        + contract.getProjectName() + "' has been updated by admin.")
                .referenceId(String.valueOf(contractId)).referenceType("CONTRACT").build());

        return result;
    }

    // ── Update status ─────────────────────────────────────────────────────────

    public ContractResponseDTO updateContractStatus(Long contractId, ContractStatus newStatus) {
        Contract       contract = findById(contractId);
        ContractStatus current  = contract.getStatus();

        if (!current.canTransitionTo(newStatus))
            throw new BadRequestException("Invalid transition: " + current + " → " + newStatus);

        contract.setStatus(newStatus);
        ContractResponseDTO result = mapToResponse(contractRepository.save(contract));

        String type, subject, message;
        switch (newStatus) {
            case PENDING    -> { type = "CONTRACT_PENDING";    subject = "Contract under review";
                message = "Dear " + contract.getVendorName() + ", your contract for '"
                        + contract.getProjectName() + "' is under review. Please respond from your dashboard."; }
            case ACTIVE     -> { type = "CONTRACT_ACTIVATED";  subject = "Contract is now ACTIVE";
                message = "Dear " + contract.getVendorName() + ", your contract for '"
                        + contract.getProjectName() + "' is now ACTIVE."; }
            case COMPLETED  -> { type = "CONTRACT_COMPLETED";  subject = "Contract completed";
                message = "Dear " + contract.getVendorName() + ", your contract for '"
                        + contract.getProjectName() + "' is COMPLETED. Thank you!"; }
            case TERMINATED -> { type = "CONTRACT_TERMINATED"; subject = "Contract terminated";
                message = "Dear " + contract.getVendorName() + ", your contract for '"
                        + contract.getProjectName() + "' has been TERMINATED."; }
            case EXPIRED    -> { type = "CONTRACT_EXPIRED";    subject = "Contract expired";
                message = "Dear " + contract.getVendorName() + ", your contract for '"
                        + contract.getProjectName() + "' has EXPIRED."; }
            default         -> { type = "GENERAL";             subject = "Contract status updated";
                message = "Dear " + contract.getVendorName() + ", status: " + current + " → " + newStatus; }
        }

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(contract.getVendorUsername()).recipientName(contract.getVendorName())
                .type(type).subject(subject).message(message)
                .referenceId(String.valueOf(contract.getContractId())).referenceType("CONTRACT").build());

        return result;
    }

    // ── Vendor respond ────────────────────────────────────────────────────────

    public ContractResponseDTO vendorRespondToContract(Long contractId, String action,
                                                       String remarks, Long requestVendorId) {
        Contract contract = findById(contractId);

        if (contract.getStatus() != ContractStatus.PENDING)
            throw new BadRequestException("Vendor can only respond to PENDING contracts. Current: " + contract.getStatus());
        if (!Objects.equals(contract.getVendorId(), requestVendorId))
            throw new BadRequestException("You are not the vendor assigned to contract #" + contractId);

        boolean isAccept = action.equalsIgnoreCase("ACCEPT");
        if (!isAccept) {
            if (remarks == null || remarks.isBlank())
                throw new BadRequestException("Remarks are required when rejecting a contract.");
            contract.setVendorRemarks(remarks);
        }

        contract.setStatus(isAccept ? ContractStatus.ACTIVE : ContractStatus.REJECTED);
        ContractResponseDTO result = mapToResponse(contractRepository.save(contract));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail("").recipientName(contract.getVendorName())
                .type(isAccept ? "CONTRACT_VENDOR_ACCEPTED" : "CONTRACT_VENDOR_REJECTED")
                .subject(isAccept ? "Vendor accepted contract #" + contractId : "Vendor rejected contract #" + contractId)
                .message(isAccept
                        ? "Vendor " + contract.getVendorName() + " ACCEPTED contract #" + contractId + " for '" + contract.getProjectName() + "'."
                        : "Vendor " + contract.getVendorName() + " REJECTED contract #" + contractId + " for '" + contract.getProjectName() + "'. Reason: " + remarks)
                .referenceId(String.valueOf(contractId)).referenceType("CONTRACT").build());

        return result;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteContract(Long contractId) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT)
            throw new BadRequestException("Only DRAFT contracts can be deleted. Current: " + contract.getStatus());

        String vendorUsername = contract.getVendorUsername();
        String vendorName     = contract.getVendorName();
        String projectName    = contract.getProjectName();
        contractRepository.delete(contract);

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(vendorUsername).recipientName(vendorName)
                .type("CONTRACT_DELETED").subject("Your contract has been deleted")
                .message("Dear " + vendorName + ", your DRAFT contract for project '" + projectName + "' has been deleted.")
                .referenceId(String.valueOf(contractId)).referenceType("CONTRACT").build());
    }

    // ── Terms ─────────────────────────────────────────────────────────────────

    public ContractTermResponseDTO addContractTerm(Long contractId, ContractTermRequestDTO request) {
        Contract contract = findById(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT)
            throw new BadRequestException("Terms can only be added to DRAFT contracts. Current: " + contract.getStatus());

        ContractTerm term = ContractTerm.builder()
                .contract(contract).description(request.getDescription())
                .complianceFlag(request.getComplianceFlag() != null ? request.getComplianceFlag() : false)
                .sequenceNumber(request.getSequenceNumber()).build();

        ContractTermResponseDTO result = mapTermToResponse(contractTermRepository.save(term));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(contract.getVendorUsername()).recipientName(contract.getVendorName())
                .type("CONTRACT_TERM_ADDED").subject("A new term added to your contract")
                .message("Dear " + contract.getVendorName() + ", a new term has been added to your contract for '"
                        + contract.getProjectName() + "': " + request.getDescription())
                .referenceId(String.valueOf(contractId)).referenceType("CONTRACT").build());

        return result;
    }

    @Transactional(readOnly = true)
    public List<ContractTermResponseDTO> getContractTerms(Long contractId) {
        findById(contractId);
        return contractTermRepository.findByContractContractIdOrderBySequenceNumberAsc(contractId)
                .stream().map(this::mapTermToResponse).collect(Collectors.toList());
    }

    public ContractTermResponseDTO editContractTerm(Long termId, ContractTermRequestDTO request) {
        ContractTerm term = findTermById(termId);
        if (term.getContract().getStatus() != ContractStatus.DRAFT)
            throw new BadRequestException("Terms can only be edited on DRAFT contracts.");

        if (request.getDescription()    != null) term.setDescription(request.getDescription());
        if (request.getComplianceFlag() != null) term.setComplianceFlag(request.getComplianceFlag());
        if (request.getSequenceNumber() != null) term.setSequenceNumber(request.getSequenceNumber());

        ContractTermResponseDTO result = mapTermToResponse(contractTermRepository.save(term));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(term.getContract().getVendorUsername()).recipientName(term.getContract().getVendorName())
                .type("CONTRACT_TERM_EDITED").subject("A term in your contract has been updated")
                .message("Dear " + term.getContract().getVendorName() + ", a term in your contract for '"
                        + term.getContract().getProjectName() + "' has been updated.")
                .referenceId(String.valueOf(term.getContract().getContractId())).referenceType("CONTRACT").build());

        return result;
    }

    public void deleteContractTerm(Long termId) {
        ContractTerm term = findTermById(termId);
        if (term.getContract().getStatus() != ContractStatus.DRAFT)
            throw new BadRequestException("Terms can only be deleted on DRAFT contracts.");

        String vendorUsername = term.getContract().getVendorUsername();
        String vendorName     = term.getContract().getVendorName();
        String projectName    = term.getContract().getProjectName();
        String termDesc       = term.getDescription();
        Long   contractId     = term.getContract().getContractId();
        contractTermRepository.delete(term);

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(vendorUsername).recipientName(vendorName)
                .type("CONTRACT_TERM_DELETED").subject("A term removed from your contract")
                .message("Dear " + vendorName + ", a term has been removed from your contract for '"
                        + projectName + "'. Removed: " + termDesc)
                .referenceId(String.valueOf(contractId)).referenceType("CONTRACT").build());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> fetchVendor(Long vendorId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = vendorServiceClient.getVendorById(vendorId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Vendor", "id", vendorId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        }
        if (VendorServiceFallback.MARKER.equals(response.getMessage()))
            throw new ServiceUnavailableException("Vendor Service is currently unavailable.");
        if (!response.isSuccess() || response.getData() == null)
            throw new ResourceNotFoundException("Vendor", "id", vendorId);
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
                .status(c.getStatus()).description(c.getDescription()).vendorRemarks(c.getVendorRemarks())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    private ContractTermResponseDTO mapTermToResponse(ContractTerm t) {
        return ContractTermResponseDTO.builder()
                .termId(t.getTermId()).contractId(t.getContract().getContractId())
                .description(t.getDescription()).complianceFlag(t.getComplianceFlag())
                .sequenceNumber(t.getSequenceNumber()).createdAt(t.getCreatedAt()).build();
    }
}
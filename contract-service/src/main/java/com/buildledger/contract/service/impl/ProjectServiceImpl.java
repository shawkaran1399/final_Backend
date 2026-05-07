package com.buildledger.contract.service.impl;

import com.buildledger.contract.event.NotificationEvent;
import com.buildledger.contract.event.NotificationProducer;
import com.buildledger.contract.dto.request.ProjectRequestDTO;
import com.buildledger.contract.dto.response.ApiResponseDTO;
import com.buildledger.contract.dto.response.ProjectResponseDTO;
import com.buildledger.contract.entity.Project;
import com.buildledger.contract.enums.ProjectStatus;
import com.buildledger.contract.exception.BadRequestException;
import com.buildledger.contract.exception.ResourceNotFoundException;
import com.buildledger.contract.exception.ServiceUnavailableException;
import com.buildledger.contract.feign.IamServiceClient;
import com.buildledger.contract.feign.IamServiceFallback;
import com.buildledger.contract.repository.ProjectRepository;
import com.buildledger.contract.service.ProjectService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final IamServiceClient iamServiceClient;
    private final NotificationProducer notificationProducer;

    @Override
    public ProjectResponseDTO createProject(ProjectRequestDTO request) {
        log.info("Creating project: {}", request.getName());

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        // ← existing logic unchanged — now also gets username
        Map<String, String> managerInfo = validateAndGetManagerInfo(request.getManagerId());
        String managerName     = managerInfo.get("name");
        String managerUsername = managerInfo.get("username");

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .location(request.getLocation())
                .budget(request.getBudget())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .managerId(request.getManagerId())
                .managerName(managerName)
                .build();

        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(managerUsername)   // ← pm_john username
                .recipientName(managerName)
                .type("PROJECT_CREATED")
                .subject("You have been assigned to a new project: " + request.getName())
                .message("Dear " + managerName + ", you have been assigned as Project Manager for '"
                        + request.getName() + "' at location '" + request.getLocation()
                        + "'. Budget: " + request.getBudget()
                        + ". Start date: " + request.getStartDate()
                        + ", End date: " + request.getEndDate())
                .referenceId(String.valueOf(result.getProjectId()))
                .referenceType("PROJECT")
                .build());

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponseDTO getProjectById(Long projectId) {
        return mapToResponse(findById(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getAllProjects() {
        return projectRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByManager(Long managerId) {
        return projectRepository.findByManagerId(managerId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public ProjectResponseDTO updateProject(Long projectId, ProjectRequestDTO request) {
        Project project = findById(projectId);

        if (request.getEndDate() != null && request.getStartDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        // ← Declare ALL variables BEFORE if blocks
        List<String> changes = new ArrayList<>();
        String managerUsername = getManagerUsername(project.getManagerId());
        String managerName = project.getManagerName();

        if (request.getName() != null && !request.getName().equals(project.getName())) {
            changes.add("Name: '" + project.getName() + "' → '" + request.getName() + "'");
            project.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().equals(project.getDescription())) {
            changes.add("Description updated");
            project.setDescription(request.getDescription());
        }
        if (request.getLocation() != null && !request.getLocation().equals(project.getLocation())) {
            changes.add("Location: '" + project.getLocation() + "' → '" + request.getLocation() + "'");
            project.setLocation(request.getLocation());
        }
        if (request.getBudget() != null && !request.getBudget().equals(project.getBudget())) {
            changes.add("Budget: " + project.getBudget() + " → " + request.getBudget());
            project.setBudget(request.getBudget());
        }
        if (request.getStartDate() != null && !request.getStartDate().equals(project.getStartDate())) {
            changes.add("Start date: " + project.getStartDate() + " → " + request.getStartDate());
            project.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null && !request.getEndDate().equals(project.getEndDate())) {
            changes.add("End date: " + project.getEndDate() + " → " + request.getEndDate());
            project.setEndDate(request.getEndDate());
        }

        if (request.getManagerId() != null
                && !request.getManagerId().equals(project.getManagerId())) {
            Map<String, String> newManagerInfo = validateAndGetManagerInfo(request.getManagerId());
            String newManagerName     = newManagerInfo.get("name");
            String newManagerUsername = newManagerInfo.get("username");

            changes.add("Manager: '" + project.getManagerName() + "' → '" + newManagerName + "'");

            project.setManagerId(request.getManagerId());
            project.setManagerName(newManagerName);

            // Notify NEW manager they have been assigned
            notificationProducer.send("contract-events", NotificationEvent.builder()
                    .recipientEmail(newManagerUsername)
                    .recipientName(newManagerName)
                    .type("PROJECT_CREATED")
                    .subject("You have been assigned to project: " + project.getName())
                    .message("Dear " + newManagerName + ", you have been assigned as "
                            + "Project Manager for '" + project.getName() + "'.")
                    .referenceId(String.valueOf(project.getProjectId()))
                    .referenceType("PROJECT")
                    .build());

            // Update outer variables to point to new manager
            managerUsername = newManagerUsername;
            managerName     = newManagerName;
        }

        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        // Send PROJECT_UPDATED notification with changed fields
        if (!changes.isEmpty()) {
            String changesText = String.join(", ", changes);
            notificationProducer.send("contract-events", NotificationEvent.builder()
                    .recipientEmail(managerUsername)
                    .recipientName(managerName)
                    .type("PROJECT_UPDATED")
                    .subject("Project updated: " + project.getName())
                    .message("Dear " + managerName + ", your project '"
                            + project.getName() + "' has been updated by admin. "
                            + "Changes: " + changesText)
                    .referenceId(String.valueOf(project.getProjectId()))
                    .referenceType("PROJECT")
                    .build());
        }

        return result;
    }
    @Override
    public ProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus newStatus) {
        log.info("Updating project {} status to {}", projectId, newStatus);
        Project project = findById(projectId);
        ProjectStatus current = project.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new BadRequestException(
                    "Invalid status transition from " + current + " to " + newStatus +
                            ". Allowed: PLANNING→ACTIVE|CANCELLED, ACTIVE→ON_HOLD|COMPLETED|CANCELLED, ON_HOLD→ACTIVE|CANCELLED.");
        }

        if (newStatus == ProjectStatus.COMPLETED) {
            project.setActualEndDate(LocalDate.now());
        }

        project.setStatus(newStatus);
        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(getManagerUsername(project.getManagerId()))  // ← pm username
                .recipientName(project.getManagerName())
                .type("PROJECT_STATUS_CHANGED")
                .subject("Project status updated: " + project.getName())
                .message("Dear " + project.getManagerName() + ", project '"
                        + project.getName() + "' status has changed from "
                        + current + " to " + newStatus + ".")
                .referenceId(String.valueOf(project.getProjectId()))
                .referenceType("PROJECT")
                .build());

        return result;
    }

    @Override
    public void deleteProject(Long projectId) {
        projectRepository.delete(findById(projectId));
        log.info("Project deleted: id={}", projectId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // ← UPDATED: returns both name and username (replaces old validateAndGetManagerName)
    private Map<String, String> validateAndGetManagerInfo(Long managerId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = iamServiceClient.getUserById(managerId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("User", "id", managerId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("IAM Service is currently unavailable. Please try again later.");
        }
        if (IamServiceFallback.MARKER.equals(response.getMessage())) {
            throw new ServiceUnavailableException("IAM Service is currently unavailable. Please try again later.");
        }
        if (!response.isSuccess() || response.getData() == null) {
            throw new ResourceNotFoundException("User", "id", managerId);
        }
        Map<String, Object> userData = response.getData();
        log.info("=== IAM userData: {}", userData);  // ← ADD THIS
        String role = (String) userData.get("role");
        if (!"PROJECT_MANAGER".equals(role)) {
            throw new BadRequestException(
                    "User ID " + managerId + " is not a PROJECT_MANAGER. Only PROJECT_MANAGER role can manage projects.");
        }
        return Map.of(
                "name",     (String) userData.getOrDefault("name", ""),
                "username", (String) userData.getOrDefault("username", "")
        );
    }

    // ← NEW: small helper to get username safely for updateProjectStatus
    private String getManagerUsername(Long managerId) {
        try {
            return validateAndGetManagerInfo(managerId).get("username");
        } catch (Exception e) {
            log.warn("Could not fetch manager username for id={}", managerId);
            return "";
        }
    }

    private Project findById(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private ProjectResponseDTO mapToResponse(Project p) {
        return ProjectResponseDTO.builder()
                .projectId(p.getProjectId()).name(p.getName()).description(p.getDescription())
                .location(p.getLocation()).budget(p.getBudget()).startDate(p.getStartDate())
                .endDate(p.getEndDate()).actualEndDate(p.getActualEndDate()).status(p.getStatus())
                .managerId(p.getManagerId()).managerName(p.getManagerName())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();
    }
}
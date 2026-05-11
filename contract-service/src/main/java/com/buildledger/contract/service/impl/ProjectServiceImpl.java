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

    private final ProjectRepository    projectRepository;
    private final IamServiceClient     iamServiceClient;
    private final NotificationProducer notificationProducer;

    @Override
    public ProjectResponseDTO createProject(ProjectRequestDTO request) {
        log.info("Creating project: {}", request.getName());

        if (request.getEndDate().isBefore(request.getStartDate()))
            throw new BadRequestException("End date cannot be before start date");

        Map<String, String> managerInfo    = validateAndGetManagerInfo(request.getManagerId());
        String              managerName    = managerInfo.get("name");
        String              managerUsername = managerInfo.get("username");

        Project project = Project.builder()
                .name(request.getName()).description(request.getDescription())
                .location(request.getLocation()).budget(request.getBudget())
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .managerId(request.getManagerId()).managerName(managerName)
                .managerUsername(managerUsername)
                .build();

        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(managerUsername).recipientName(managerName)
                .type("PROJECT_CREATED")
                .subject("You have been assigned to a new project: " + request.getName())
                .message("Dear " + managerName + ", you have been assigned as Project Manager for '"
                        + request.getName() + "' at '" + request.getLocation()
                        + "'. Budget: " + request.getBudget()
                        + ". Start: " + request.getStartDate() + ", End: " + request.getEndDate())
                .referenceId(String.valueOf(result.getProjectId()))
                .referenceType("PROJECT").build());

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
        return projectRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByManager(Long managerId) {
        return projectRepository.findByManagerId(managerId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getMyProjects(String managerUsername) {
        log.info("Fetching projects for manager username: {}", managerUsername);
        return projectRepository.findByManagerUsername(managerUsername).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public ProjectResponseDTO updateProject(Long projectId, ProjectRequestDTO request) {
        Project project = findById(projectId);

        if (request.getEndDate() != null && request.getStartDate() != null
                && request.getEndDate().isBefore(request.getStartDate()))
            throw new BadRequestException("End date cannot be before start date");

        // CONFLICT 1 resolved: use project.getManagerUsername() — getManagerUsername() helper does not exist
        List<String> changes       = new ArrayList<>();
        String managerUsername     = project.getManagerUsername() != null ? project.getManagerUsername() : "";
        String managerName         = project.getManagerName();

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
        // CONFLICT 2 resolved: keep actualEndDate block from incoming branch
        if (request.getActualEndDate() != null) {
            project.setActualEndDate(request.getActualEndDate());
        }

        // Manager reassignment
        if (request.getManagerId() != null && !request.getManagerId().equals(project.getManagerId())) {
            Map<String, String> newInfo     = validateAndGetManagerInfo(request.getManagerId());
            String              newName     = newInfo.get("name");
            String              newUsername = newInfo.get("username");

            changes.add("Manager: '" + project.getManagerName() + "' → '" + newName + "'");
            project.setManagerId(request.getManagerId());
            project.setManagerName(newName);
            project.setManagerUsername(newUsername);

            // Notify new manager of assignment
            notificationProducer.send("contract-events", NotificationEvent.builder()
                    .recipientEmail(newUsername).recipientName(newName)
                    .type("PROJECT_MANAGER_REASSIGNED")
                    .subject("You have been assigned to project: " + project.getName())
                    .message("Dear " + newName + ", you have been assigned as Project Manager for '"
                            + project.getName() + "'.")
                    .referenceId(String.valueOf(project.getProjectId()))
                    .referenceType("PROJECT").build());

            // CONFLICT 4 resolved: use newUsername/newName — main branch referenced undefined variables
            managerUsername = newUsername;
            managerName     = newName;
        }

        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        if (!changes.isEmpty()) {
            notificationProducer.send("contract-events", NotificationEvent.builder()
                    .recipientEmail(managerUsername).recipientName(managerName)
                    .type("PROJECT_UPDATED")
                    .subject("Project updated: " + project.getName())
                    .message("Dear " + managerName + ", your project '" + project.getName()
                            + "' has been updated. Changes: " + String.join(", ", changes))
                    .referenceId(String.valueOf(project.getProjectId()))
                    .referenceType("PROJECT").build());
        }

        return result;
    }

    /**
     * PM-only update — only description and actualEndDate.
     * No budget, no dates, no manager changes allowed.
     */
    @Override
    public ProjectResponseDTO updateProjectNotes(Long projectId, String description, String actualEndDate) {
        Project project = findById(projectId);

        if (description != null)
            project.setDescription(description);

        if (actualEndDate != null && !actualEndDate.isBlank())
            project.setActualEndDate(LocalDate.parse(actualEndDate));

        log.info("PM updated notes for project: {}", projectId);
        return mapToResponse(projectRepository.save(project));
    }

    @Override
    public ProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus newStatus) {
        log.info("Updating project {} status to {}", projectId, newStatus);
        Project       project = findById(projectId);
        ProjectStatus current = project.getStatus();

        if (!current.canTransitionTo(newStatus))
            throw new BadRequestException("Invalid status transition from " + current + " to " + newStatus
                    + ". Allowed: PLANNING→ACTIVE|CANCELLED, ACTIVE→ON_HOLD|COMPLETED|CANCELLED, ON_HOLD→ACTIVE|CANCELLED.");

        if (newStatus == ProjectStatus.COMPLETED)
            project.setActualEndDate(LocalDate.now());

        project.setStatus(newStatus);
        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        // CONFLICT 5 resolved: use project.getManagerUsername() (getManagerUsername() helper does not exist)
        // Keep rich per-transition notification types from main branch, wired into the send call
        String managerUsername = project.getManagerUsername() != null ? project.getManagerUsername() : "";
        String managerName     = project.getManagerName();

        String notifType;
        String notifSubject;
        String notifMessage;

        if (newStatus == ProjectStatus.ACTIVE && current == ProjectStatus.PLANNING) {
            notifType    = "PROJECT_ACTIVATED";
            notifSubject = "Project activated: " + project.getName();
            notifMessage = "Dear " + managerName + ", your project '"
                    + project.getName() + "' has been ACTIVATED. Work can now begin!";

        } else if (newStatus == ProjectStatus.ON_HOLD) {
            notifType    = "PROJECT_PUT_ON_HOLD";
            notifSubject = "Project put on hold: " + project.getName();
            notifMessage = "Dear " + managerName + ", your project '"
                    + project.getName() + "' has been put ON HOLD by admin.";

        } else if (newStatus == ProjectStatus.ACTIVE && current == ProjectStatus.ON_HOLD) {
            notifType    = "PROJECT_RESUMED";
            notifSubject = "Project resumed: " + project.getName();
            notifMessage = "Dear " + managerName + ", your project '"
                    + project.getName() + "' has been RESUMED. Work can continue!";

        } else if (newStatus == ProjectStatus.COMPLETED) {
            notifType    = "PROJECT_COMPLETED";
            notifSubject = "Project completed: " + project.getName();
            notifMessage = "Dear " + managerName + ", your project '"
                    + project.getName() + "' has been marked as COMPLETED. "
                    + "Completion date: " + project.getActualEndDate();

        } else if (newStatus == ProjectStatus.CANCELLED) {
            notifType    = "PROJECT_CANCELLED";
            notifSubject = "Project cancelled: " + project.getName();
            notifMessage = "Dear " + managerName + ", your project '"
                    + project.getName() + "' has been CANCELLED by admin.";

        } else {
            notifType    = "PROJECT_STATUS_CHANGED";
            notifSubject = "Project status updated: " + project.getName();
            notifMessage = "Dear " + managerName + ", project '"
                    + project.getName() + "' status changed from " + current + " to " + newStatus + ".";
        }

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(managerUsername).recipientName(managerName)
                .type(notifType)
                .subject(notifSubject)
                .message(notifMessage)
                .referenceId(String.valueOf(project.getProjectId()))
                .referenceType("PROJECT").build());

        return result;
    }

    @Override
    public void deleteProject(Long projectId) {
        // CONFLICT 6 resolved: keep delete notification from main branch,
        // fix getManagerUsername() → project.getManagerUsername()
        Project project      = findById(projectId);
        String managerUsername = project.getManagerUsername() != null ? project.getManagerUsername() : "";
        String managerName   = project.getManagerName();
        String projectName   = project.getName();

        projectRepository.delete(project);
        log.info("Project deleted: id={}", projectId);

        notificationProducer.send("contract-events", NotificationEvent.builder()
                .recipientEmail(managerUsername)
                .recipientName(managerName)
                .type("PROJECT_DELETED")
                .subject("Project deleted: " + projectName)
                .message("Dear " + managerName + ", project '"
                        + projectName + "' has been permanently deleted by admin.")
                .referenceId(String.valueOf(projectId))
                .referenceType("PROJECT")
                .build());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, String> validateAndGetManagerInfo(Long managerId) {
        ApiResponseDTO<Map<String, Object>> response;
        try {
            response = iamServiceClient.getUserById(managerId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("User", "id", managerId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("IAM Service is currently unavailable.");
        }
        if (IamServiceFallback.MARKER.equals(response.getMessage()))
            throw new ServiceUnavailableException("IAM Service is currently unavailable.");
        if (!response.isSuccess() || response.getData() == null)
            throw new ResourceNotFoundException("User", "id", managerId);

        Map<String, Object> userData = response.getData();
        log.info("=== IAM userData: {}", userData);
        String role = (String) userData.get("role");
        if (!"PROJECT_MANAGER".equals(role))
            throw new BadRequestException("User ID " + managerId + " is not a PROJECT_MANAGER.");

        return Map.of(
                "name",     (String) userData.getOrDefault("name",     ""),
                "username", (String) userData.getOrDefault("username", "")
        );
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
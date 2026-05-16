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

        sendProjectNotif("PROJECT_CREATED",
                "You have been assigned to a new project: " + request.getName(),
                "Dear " + managerName + ", you have been assigned as Project Manager for '"
                        + request.getName() + "' at '" + request.getLocation()
                        + "'. Budget: " + request.getBudget()
                        + ". Start: " + request.getStartDate() + ", End: " + request.getEndDate(),
                String.valueOf(result.getProjectId()),
                managerUsername, ADMIN_USERNAME);

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

        // fieldChanges tracks only data field edits — manager reassignment is handled separately
        List<String> fieldChanges   = new ArrayList<>();
        String       managerUsername = project.getManagerUsername() != null
                ? project.getManagerUsername() : "";
        String       managerName    = project.getManagerName();

        if (request.getName() != null && !request.getName().equals(project.getName())) {
            fieldChanges.add("Name: '" + project.getName() + "' → '" + request.getName() + "'");
            project.setName(request.getName());
        }
        if (request.getDescription() != null && !request.getDescription().equals(project.getDescription())) {
            fieldChanges.add("Description updated");
            project.setDescription(request.getDescription());
        }
        if (request.getLocation() != null && !request.getLocation().equals(project.getLocation())) {
            fieldChanges.add("Location: '" + project.getLocation() + "' → '" + request.getLocation() + "'");
            project.setLocation(request.getLocation());
        }
        if (request.getBudget() != null && !request.getBudget().equals(project.getBudget())) {
            fieldChanges.add("Budget: " + project.getBudget() + " → " + request.getBudget());
            project.setBudget(request.getBudget());
        }
        if (request.getStartDate() != null && !request.getStartDate().equals(project.getStartDate())) {
            fieldChanges.add("Start date: " + project.getStartDate() + " → " + request.getStartDate());
            project.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null && !request.getEndDate().equals(project.getEndDate())) {
            fieldChanges.add("End date: " + project.getEndDate() + " → " + request.getEndDate());
            project.setEndDate(request.getEndDate());
        }
        if (request.getActualEndDate() != null) {
            project.setActualEndDate(request.getActualEndDate());
        }

        if (request.getManagerId() != null && !request.getManagerId().equals(project.getManagerId())) {
            Map<String, String> newInfo     = validateAndGetManagerInfo(request.getManagerId());
            String              newName     = newInfo.get("name");
            String              newUsername = newInfo.get("username");

            // Capture old PM before overwriting — needed for their removal notification
            String oldManagerUsername = managerUsername;
            String oldManagerName     = managerName;

            project.setManagerId(request.getManagerId());
            project.setManagerName(newName);
            project.setManagerUsername(newUsername);

            // Notify NEW PM they have been assigned
            sendProjectNotif("PROJECT_MANAGER_REASSIGNED",
                    "You have been assigned to project: " + project.getName(),
                    "Dear " + newName + ", you have been assigned as Project Manager for '"
                            + project.getName() + "'.",
                    String.valueOf(project.getProjectId()),
                    newUsername, ADMIN_USERNAME);

            // Notify OLD PM they have been removed
            if (!oldManagerUsername.isBlank()) {
                sendProjectNotif("PROJECT_MANAGER_REASSIGNED",
                        "You have been removed from project: " + project.getName(),
                        "Dear " + oldManagerName + ", you have been removed as Project Manager for '"
                                + project.getName() + "'. The project has been reassigned to " + newName + ".",
                        String.valueOf(project.getProjectId()),
                        oldManagerUsername, ADMIN_USERNAME);
            }

            managerUsername = newUsername;
            managerName     = newName;
        }

        ProjectResponseDTO result = mapToResponse(projectRepository.save(project));

        // Only fire PROJECT_UPDATED if actual data fields changed — not for manager reassignment
        if (!fieldChanges.isEmpty()) {
            sendProjectNotif("PROJECT_UPDATED",
                    "Project updated: " + project.getName(),
                    "Dear " + managerName + ", your project '" + project.getName()
                            + "' has been updated. Changes: " + String.join(", ", fieldChanges),
                    String.valueOf(project.getProjectId()),
                    managerUsername, ADMIN_USERNAME);
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

        String username = project.getManagerUsername() != null ? project.getManagerUsername() : "";

        // Fire a specific notification type per status transition
        String notifType = switch (newStatus) {
            case ACTIVE     -> current == ProjectStatus.ON_HOLD ? "PROJECT_RESUMED" : "PROJECT_ACTIVATED";
            case ON_HOLD    -> "PROJECT_PUT_ON_HOLD";
            case COMPLETED  -> "PROJECT_COMPLETED";
            case CANCELLED  -> "PROJECT_CANCELLED";
            default         -> "PROJECT_STATUS_CHANGED";
        };

        String subject = switch (newStatus) {
            case ACTIVE     -> current == ProjectStatus.ON_HOLD
                    ? "Project resumed: " + project.getName()
                    : "Project activated: " + project.getName();
            case ON_HOLD    -> "Project put on hold: " + project.getName();
            case COMPLETED  -> "Project completed: " + project.getName();
            case CANCELLED  -> "Project cancelled: " + project.getName();
            default         -> "Project status updated: " + project.getName();
        };

        sendProjectNotif(notifType, subject,
                "Dear " + project.getManagerName() + ", project '" + project.getName()
                        + "' status changed from " + current + " to " + newStatus + ".",
                String.valueOf(project.getProjectId()),
                username, ADMIN_USERNAME);

        return result;
    }

    @Override
    public void deleteProject(Long projectId) {
        Project project = findById(projectId);
        String username = project.getManagerUsername() != null ? project.getManagerUsername() : "";
        projectRepository.delete(project);
        log.info("Project deleted: id={}", projectId);

        sendProjectNotif("PROJECT_DELETED",
                "Project deleted: " + project.getName(),
                "Dear " + project.getManagerName() + ", project '" + project.getName()
                        + "' has been permanently deleted.",
                String.valueOf(projectId),
                username, ADMIN_USERNAME);
    }

    private static final String ADMIN_USERNAME = "admin";

    private void sendProjectNotif(String type, String subject, String message,
                                  String refId, String... recipients) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String r : recipients) { if (r != null && !r.isBlank()) seen.add(r); }
        for (String r : seen) {
            notificationProducer.send("contract-events", NotificationEvent.builder()
                    .recipientEmail(r).recipientName(r)
                    .type(type).subject(subject).message(message)
                    .referenceId(refId).referenceType("PROJECT").build());
        }
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
                "username", (String) userData.getOrDefault("username", ""),
                "email",    (String) userData.getOrDefault("email",    "")
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
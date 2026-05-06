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
    private final NotificationProducer notificationProducer;  // ← ADD THIS

    @Override
    public ProjectResponseDTO createProject(ProjectRequestDTO request) {
        log.info("Creating project: {}", request.getName());

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        String managerName = validateAndGetManagerName(request.getManagerId());

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
                .recipientEmail("")
                .recipientName(managerName)
                .type("PROJECT_CREATED")
                .subject("New project created: " + request.getName())
                .message("A new project '" + request.getName() + "' has been created at location '"
                        + request.getLocation() + "' with budget " + request.getBudget()
                        + ". Start date: " + request.getStartDate() + ", End date: " + request.getEndDate())
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

        if (request.getName() != null) project.setName(request.getName());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getLocation() != null) project.setLocation(request.getLocation());
        if (request.getBudget() != null) project.setBudget(request.getBudget());
        if (request.getStartDate() != null) project.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) project.setEndDate(request.getEndDate());

        if (request.getManagerId() != null) {
            String managerName = validateAndGetManagerName(request.getManagerId());
            project.setManagerId(request.getManagerId());
            project.setManagerName(managerName);
        }

        return mapToResponse(projectRepository.save(project));
    }

    @Override
    public ProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus  newStatus) {
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
                .recipientEmail("")
                .recipientName(project.getManagerName())
                .type("PROJECT_STATUS_CHANGED")
                .subject("Project status updated: " + project.getName())
                .message("Project '" + project.getName() + "' status has changed from "
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

    private String validateAndGetManagerName(Long managerId) {
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
        String role = (String) userData.get("role");
        if (!"PROJECT_MANAGER".equals(role)) {
            throw new BadRequestException(
                "User ID " + managerId + " is not a PROJECT_MANAGER. Only PROJECT_MANAGER role can manage projects.");
        }
        return (String) userData.get("name");
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


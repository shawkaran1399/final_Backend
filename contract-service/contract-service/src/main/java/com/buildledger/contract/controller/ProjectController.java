package com.buildledger.contract.controller;

import com.buildledger.contract.dto.request.ProjectRequestDTO;
import com.buildledger.contract.dto.response.ApiResponseDTO;
import com.buildledger.contract.dto.response.ProjectResponseDTO;
import com.buildledger.contract.enums.ProjectStatus;
import com.buildledger.contract.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Project Management")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create project [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> createProject(
            @Valid @RequestBody ProjectRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponseDTO.success("Project created successfully", projectService.createProject(request)));
    }

    @GetMapping
    @Operation(summary = "Get all projects [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ProjectResponseDTO>>> getAllProjects() {
        return ResponseEntity.ok(ApiResponseDTO.success("Projects retrieved", projectService.getAllProjects()));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> getProjectById(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Project retrieved", projectService.getProjectById(projectId)));
    }

    @GetMapping("/manager/{managerId}")
    @Operation(summary = "Get projects by manager [ALL roles]")
    public ResponseEntity<ApiResponseDTO<List<ProjectResponseDTO>>> getProjectsByManager(
            @PathVariable Long managerId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Projects retrieved",
            projectService.getProjectsByManager(managerId)));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update project [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequestDTO request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Project updated",
            projectService.updateProject(projectId, request)));
    }

    @PatchMapping("/{projectId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update project status [ADMIN / PROJECT_MANAGER]",
               description = "Lifecycle: PLANNING → ACTIVE | CANCELLED, ACTIVE → ON_HOLD | COMPLETED | CANCELLED, ON_HOLD → ACTIVE | CANCELLED")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> updateProjectStatus(
            @PathVariable Long projectId,
            @RequestParam ProjectStatus newStatus) {
        return ResponseEntity.ok(ApiResponseDTO.success("Project status updated",
            projectService.updateProjectStatus(projectId, newStatus)));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete project [ADMIN only]")
    public ResponseEntity<ApiResponseDTO<Void>> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.ok(ApiResponseDTO.success("Project deleted successfully"));
    }
}


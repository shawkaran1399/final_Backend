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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    @Operation(summary = "Get all projects [ALL authenticated roles]")
    public ResponseEntity<ApiResponseDTO<List<ProjectResponseDTO>>> getAllProjects() {
        return ResponseEntity.ok(ApiResponseDTO.success("Projects retrieved", projectService.getAllProjects()));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Get projects assigned to the logged-in project manager [PROJECT_MANAGER only]")
    public ResponseEntity<ApiResponseDTO<List<ProjectResponseDTO>>> getMyProjects(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDTO.success("Projects retrieved",
                projectService.getMyProjects(authentication.getName())));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID [ALL roles]")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> getProjectById(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Project retrieved",
                projectService.getProjectById(projectId)));
    }

    @GetMapping("/manager/{managerId}")
    @Operation(summary = "Get projects by manager ID [ALL roles]")
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

    /**
     * PM-only endpoint — only description and actualEndDate can be updated.
     * Accepts a simple JSON body: { "description": "...", "actualEndDate": "2026-05-10" }
     */
    @PatchMapping("/{projectId}/notes")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update project notes [PROJECT_MANAGER only]",
            description = "PM can update description and actual end date only.")
    public ResponseEntity<ApiResponseDTO<ProjectResponseDTO>> updateProjectNotes(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body) {
        String description   = body.get("description");
        String actualEndDate = body.get("actualEndDate");
        return ResponseEntity.ok(ApiResponseDTO.success("Project notes updated",
                projectService.updateProjectNotes(projectId, description, actualEndDate)));
    }

    @PatchMapping("/{projectId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PROJECT_MANAGER')")
    @Operation(summary = "Update project status [ADMIN / PROJECT_MANAGER]",
            description = "PLANNING→ACTIVE|CANCELLED, ACTIVE→ON_HOLD|COMPLETED|CANCELLED, ON_HOLD→ACTIVE|CANCELLED")
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
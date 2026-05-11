package com.buildledger.contract.service;

import com.buildledger.contract.dto.request.ProjectRequestDTO;
import com.buildledger.contract.dto.response.ProjectResponseDTO;
import com.buildledger.contract.enums.ProjectStatus;
import java.util.List;

public interface ProjectService {
    ProjectResponseDTO createProject(ProjectRequestDTO request);
    ProjectResponseDTO getProjectById(Long projectId);
    List<ProjectResponseDTO> getAllProjects();
    List<ProjectResponseDTO> getProjectsByManager(Long managerId);
    List<ProjectResponseDTO> getMyProjects(String managerUsername);
    ProjectResponseDTO updateProject(Long projectId, ProjectRequestDTO request);
    // PM-only: update description and actual end date
    ProjectResponseDTO updateProjectNotes(Long projectId, String description, String actualEndDate);
    ProjectResponseDTO updateProjectStatus(Long projectId, ProjectStatus newStatus);
    void deleteProject(Long projectId);
}
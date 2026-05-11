package com.buildledger.contract.repository;

import com.buildledger.contract.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByManagerId(Long managerId);
    List<Project> findByManagerName(String managerName);
    // Search by username (JWT principal) — matches managerUsername stored at creation time
    List<Project> findByManagerUsername(String managerUsername);
}
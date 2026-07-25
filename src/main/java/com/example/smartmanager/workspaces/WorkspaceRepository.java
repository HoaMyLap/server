package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, java.util.UUID> {
    List<WorkspaceEntity> findByOwnerId(java.util.UUID ownerId);
}

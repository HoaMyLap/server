package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {
    
    List<WorkspaceMemberEntity> findByIdWorkspaceId(UUID workspaceId);
    
    List<WorkspaceMemberEntity> findByIdUserId(UUID userId);

    @Query("SELECT wm.role FROM WorkspaceMemberEntity wm WHERE wm.id.workspaceId = :workspaceId AND wm.id.userId = :userId")
    Optional<String> findRoleByWorkspaceIdAndUserId(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @Query("SELECT new com.example.smartmanager.workspaces.WorkspaceMemberDto(u.id, u.email, u.fullname, u.avatarUrl, wm.role) " +
           "FROM WorkspaceMemberEntity wm " +
           "JOIN com.example.smartmanager.users.UserEntity u ON wm.id.userId = u.id " +
           "WHERE wm.id.workspaceId = :workspaceId")
    List<WorkspaceMemberDto> findMembersWithDetails(@Param("workspaceId") UUID workspaceId);
}

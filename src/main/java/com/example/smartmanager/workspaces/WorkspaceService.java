package com.example.smartmanager.workspaces;

import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

import com.example.smartmanager.email.EmailJob;
import com.example.smartmanager.email.EmailWorker;
import com.example.smartmanager.notifications.NotificationService;
import org.springframework.data.redis.core.RedisTemplate;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public WorkspaceEntity createWorkspace(WorkspaceEntity workspace, String ownerId) {
        workspace.setOwnerId(UUID.fromString(ownerId));
        WorkspaceEntity savedWorkspace = workspaceRepository.save(workspace);

        // Thêm người tạo làm ADMIN trong bảng workspace_members
        WorkspaceMemberId memberId = new WorkspaceMemberId(savedWorkspace.getId(), UUID.fromString(ownerId));
        WorkspaceMemberEntity member = new WorkspaceMemberEntity(memberId, "ADMIN", java.time.LocalDateTime.now());
        workspaceMemberRepository.save(member);

        return savedWorkspace;
    }

    @Transactional
    public void addMember(String workspaceId, String email, String role) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên với email này"));

        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), user.getId());
        
        if (workspaceMemberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("Thành viên đã ở trong không gian làm việc này rồi");
        }

        WorkspaceMemberEntity member = new WorkspaceMemberEntity(memberId, role, java.time.LocalDateTime.now());
        workspaceMemberRepository.save(member);

        // Lấy tên Workspace
        WorkspaceEntity ws = workspaceRepository.findById(UUID.fromString(workspaceId)).orElse(null);
        String wsName = ws != null ? ws.getName() : "Workspace";

        // 1. Tạo thông báo hệ thống
        notificationService.createNotification(
                user.getId().toString(),
                "Bạn được mời vào Workspace mới",
                "Bạn đã được thêm vào không gian làm việc \"" + wsName + "\" với vai trò: " + role,
                "WORKSPACE"
        );

        // 2. Đẩy Email Background Job vào Redis Queue
        EmailJob job = new EmailJob(
                email,
                workspaceId,
                wsName,
                role,
                "Admin",
                "Lời mời tham gia không gian làm việc " + wsName,
                "Bạn vừa được mời tham gia không gian làm việc " + wsName + " với vai trò " + role
        );
        redisTemplate.opsForList().leftPush(EmailWorker.QUEUE_NAME, job);
    }

    public List<WorkspaceEntity> getUserWorkspaces(String userId) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.findByIdUserId(UUID.fromString(userId));
        List<UUID> workspaceIds = memberships.stream().map(m -> m.getId().getWorkspaceId()).toList();
        return workspaceRepository.findAllById(workspaceIds);
    }

    public List<WorkspaceMemberDto> getWorkspaceMembers(String workspaceId) {
        return workspaceMemberRepository.findMembersWithDetails(UUID.fromString(workspaceId));
    }

    @Transactional
    public void removeMember(String workspaceId, String userId) {
        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), UUID.fromString(userId));
        if (!workspaceMemberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("Thành viên không tồn tại trong không gian làm việc này");
        }
        workspaceMemberRepository.deleteById(memberId);
    }

    @Transactional
    public void updateMemberRole(String workspaceId, String userId, String newRole) {
        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), UUID.fromString(userId));
        WorkspaceMemberEntity member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Thành viên không tồn tại trong không gian làm việc này"));
        member.setRole(newRole);
        workspaceMemberRepository.save(member);
    }
}

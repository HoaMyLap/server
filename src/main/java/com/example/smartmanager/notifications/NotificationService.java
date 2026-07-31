package com.example.smartmanager.notifications;

import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import com.example.smartmanager.workspaces.WorkspaceEntity;
import com.example.smartmanager.workspaces.WorkspaceRepository;
import com.example.smartmanager.workspaces.WorkspaceMemberEntity;
import com.example.smartmanager.workspaces.WorkspaceMemberId;
import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
import com.example.smartmanager.projects.ProjectEntity;
import com.example.smartmanager.projects.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.smartmanager.projects.ProjectMemberEntity;
import com.example.smartmanager.projects.ProjectMemberId;
import com.example.smartmanager.projects.ProjectMemberRepository;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationEntity> getUserNotifications(String userId) {
        List<NotificationEntity> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
        for (NotificationEntity n : list) {
            if (n.getInvitationId() != null) {
                invitationRepository.findById(n.getInvitationId())
                        .ifPresent(inv -> n.setInvitationStatus(inv.getStatus()));
            }
        }
        return list;
    }

    @Transactional
    public NotificationEntity markAsRead(String notificationId) {
        NotificationEntity notification = notificationRepository.findById(UUID.fromString(notificationId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        List<NotificationEntity> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
        for (NotificationEntity n : list) {
            if (!Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        }
    }

    @Transactional
    public NotificationEntity createNotification(String userId, String title, String content, String type) {
        return createNotificationWithInvitation(userId, title, content, type, null);
    }

    @Transactional
    public NotificationEntity createNotificationWithInvitation(String userId, String title, String content, String type, UUID invitationId) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(UUID.fromString(userId));
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setType(type);
        notification.setInvitationId(invitationId);
        notification.setCreatedAt(LocalDateTime.now());

        NotificationEntity saved = notificationRepository.save(notification);

        // Phát tin nhắn realtime qua WebSocket cho riêng user
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, saved);

        return saved;
    }

    /**
     * Gửi lời mời gia nhập Workspace hoặc Dự án (Chỉ Admin được gửi)
     */
    @Transactional
    public void inviteUser(InviteUserRequest req, String inviterId) {
        UserEntity inviter = userRepository.findById(UUID.fromString(inviterId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng gửi lời mời"));

        UserEntity invitee = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + req.getEmail()));

        if (inviter.getId().equals(invitee.getId())) {
            throw new IllegalArgumentException("Bạn không thể tự mời chính mình");
        }

        String targetName = "";
        UUID workspaceIdForProject = null;

        if ("WORKSPACE".equalsIgnoreCase(req.getTargetType())) {
            WorkspaceEntity ws = workspaceRepository.findById(UUID.fromString(req.getTargetId()))
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Workspace"));
            targetName = ws.getName();

            // Kiểm tra xem đã là thành viên chưa
            Optional<WorkspaceMemberEntity> existing = workspaceMemberRepository.findById(new WorkspaceMemberId(ws.getId(), invitee.getId()));
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Người dùng " + invitee.getEmail() + " đã là thành viên của Workspace này");
            }
        } else if ("PROJECT".equalsIgnoreCase(req.getTargetType())) {
            ProjectEntity proj = projectRepository.findById(UUID.fromString(req.getTargetId()))
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Dự án"));
            targetName = proj.getName();
            workspaceIdForProject = proj.getWorkspaceId();

            Optional<WorkspaceMemberEntity> existing = workspaceMemberRepository.findById(new WorkspaceMemberId(workspaceIdForProject, invitee.getId()));
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Người dùng " + invitee.getEmail() + " đã có quyền truy cập dự án này");
            }
        } else {
            throw new IllegalArgumentException("Loại mục tiêu mời không hợp lệ (Phải là WORKSPACE hoặc PROJECT)");
        }

        // Tạo bản ghi Invitation
        InvitationEntity invitation = new InvitationEntity();
        invitation.setTargetType(req.getTargetType().toUpperCase());
        invitation.setTargetId(UUID.fromString(req.getTargetId()));
        invitation.setInviterId(inviter.getId());
        invitation.setInviteeId(invitee.getId());
        invitation.setRole(req.getRole() != null ? req.getRole().toUpperCase() : "MEMBER");
        invitation.setType("INVITATION");
        invitation.setStatus("PENDING");
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setUpdatedAt(LocalDateTime.now());

        InvitationEntity savedInv = invitationRepository.save(invitation);

        // Tạo Notification cho người được mời
        String title = "Lời mời tham gia " + ("WORKSPACE".equalsIgnoreCase(req.getTargetType()) ? "Workspace" : "Dự án");
        String content = inviter.getFullname() + " đã mời bạn tham gia " +
                ("WORKSPACE".equalsIgnoreCase(req.getTargetType()) ? "Workspace" : "Dự án") +
                " \"" + targetName + "\" với vai trò " + invitation.getRole() + ".";

        createNotificationWithInvitation(invitee.getId().toString(), title, content, "INVITATION", savedInv.getId());
    }

    /**
     * Người dùng Đồng ý / Từ chối lời mời
     */
    @Transactional
    public NotificationEntity respondInvitation(String notificationId, String action, String currentUserId) {
        NotificationEntity notification = notificationRepository.findById(UUID.fromString(notificationId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));

        if (!notification.getUserId().toString().equals(currentUserId)) {
            throw new IllegalArgumentException("Bạn không có quyền phản hồi thông báo này");
        }

        if (notification.getInvitationId() == null) {
            throw new IllegalArgumentException("Thông báo này không chứa lời mời hợp lệ");
        }

        InvitationEntity invitation = invitationRepository.findById(notification.getInvitationId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin lời mời"));

        if (!"PENDING".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException("Lời mời này đã được xử lý trước đó");
        }

        UserEntity user = userRepository.findById(UUID.fromString(currentUserId)).orElseThrow();
        String targetName = "";
        UUID workspaceIdToJoin = invitation.getTargetId();

        if ("PROJECT".equalsIgnoreCase(invitation.getTargetType())) {
            ProjectEntity proj = projectRepository.findById(invitation.getTargetId()).orElseThrow();
            targetName = proj.getName();
            workspaceIdToJoin = proj.getWorkspaceId();
        } else {
            WorkspaceEntity ws = workspaceRepository.findById(invitation.getTargetId()).orElseThrow();
            targetName = ws.getName();
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            invitation.setStatus("ACCEPTED");
            invitation.setUpdatedAt(LocalDateTime.now());
            invitationRepository.save(invitation);

            if ("PROJECT".equalsIgnoreCase(invitation.getTargetType())) {
                // Thêm vào Workspace Member (role VIEWER) nếu chưa là thành viên workspace
                WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceIdToJoin, user.getId());
                if (!workspaceMemberRepository.existsById(memberId)) {
                    workspaceMemberRepository.save(new WorkspaceMemberEntity(memberId, "VIEWER", LocalDateTime.now()));
                }
                // Thêm vào project_members cho riêng dự án được mời
                ProjectMemberId pmId = new ProjectMemberId(invitation.getTargetId(), user.getId());
                projectMemberRepository.save(new ProjectMemberEntity(pmId, invitation.getRole(), LocalDateTime.now()));

                // Phát tín nhắn realtime cho dự án qua WebSocket
                try {
                    java.util.Map<String, Object> socketMsg = new java.util.HashMap<>();
                    socketMsg.put("action", "ADD_MEMBER");
                    socketMsg.put("projectId", invitation.getTargetId().toString());
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("userId", user.getId().toString());
                    payload.put("email", user.getEmail());
                    payload.put("fullname", user.getFullname());
                    payload.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    payload.put("role", invitation.getRole());
                    socketMsg.put("payload", payload);
                    messagingTemplate.convertAndSend("/topic/projects/" + invitation.getTargetId().toString(), socketMsg);
                } catch (Exception e) {
                    System.err.println("Failed to broadcast project member add: " + e.getMessage());
                }
            } else {
                // Thêm vào Workspace Member với vai trò được mời
                WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceIdToJoin, user.getId());
                workspaceMemberRepository.save(new WorkspaceMemberEntity(memberId, invitation.getRole(), LocalDateTime.now()));

                // Thêm người dùng vào tất cả dự án hiện có của Workspace
                List<ProjectEntity> allProjects = projectRepository.findByWorkspaceId(workspaceIdToJoin);
                for (ProjectEntity p : allProjects) {
                    ProjectMemberId pmId = new ProjectMemberId(p.getId(), user.getId());
                    projectMemberRepository.save(new ProjectMemberEntity(pmId, invitation.getRole(), LocalDateTime.now()));
                }

                // Phát tín nhắn realtime cho Workspace và toàn bộ các Dự án con
                try {
                    java.util.Map<String, Object> socketMsg = new java.util.HashMap<>();
                    socketMsg.put("action", "ADD_MEMBER");
                    socketMsg.put("workspaceId", workspaceIdToJoin.toString());
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("userId", user.getId().toString());
                    payload.put("email", user.getEmail());
                    payload.put("fullname", user.getFullname());
                    payload.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    payload.put("role", invitation.getRole());
                    socketMsg.put("payload", payload);

                    // Gửi đến workspace topic
                    messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceIdToJoin.toString(), socketMsg);

                    // Gửi đến từng dự án trong workspace
                    for (ProjectEntity p : allProjects) {
                        java.util.Map<String, Object> pSocketMsg = new java.util.HashMap<>();
                        pSocketMsg.put("action", "ADD_MEMBER");
                        pSocketMsg.put("projectId", p.getId().toString());
                        pSocketMsg.put("payload", payload);
                        messagingTemplate.convertAndSend("/topic/projects/" + p.getId().toString(), pSocketMsg);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to broadcast workspace/project member add: " + e.getMessage());
                }
            }

            // Gửi thông báo cho người mời
            createNotification(
                    invitation.getInviterId().toString(),
                    "Lời mời đã được chấp nhận",
                    user.getFullname() + " đã đồng ý tham gia " +
                            ("WORKSPACE".equalsIgnoreCase(invitation.getTargetType()) ? "Workspace" : "Dự án") +
                            " \"" + targetName + "\".",
                    "WORKSPACE"
            );
        } else {
            invitation.setStatus("REJECTED");
            invitation.setUpdatedAt(LocalDateTime.now());
            invitationRepository.save(invitation);

            // Gửi thông báo cho người mời
            createNotification(
                    invitation.getInviterId().toString(),
                    "Lời mời bị từ chối",
                    user.getFullname() + " đã từ chối tham gia " +
                            ("WORKSPACE".equalsIgnoreCase(invitation.getTargetType()) ? "Workspace" : "Dự án") +
                            " \"" + targetName + "\".",
                    "WORKSPACE"
            );
        }

        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    /**
     * Người dùng tạo yêu cầu rời Workspace hoặc Dự án
     */
    @Transactional
    public void requestLeave(LeaveRequest req, String currentUserId) {
        UserEntity user = userRepository.findById(UUID.fromString(currentUserId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        String targetName = "";
        UUID workspaceId = null;

        if ("WORKSPACE".equalsIgnoreCase(req.getTargetType())) {
            workspaceId = UUID.fromString(req.getTargetId());
            WorkspaceEntity ws = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Workspace"));
            targetName = ws.getName();
        } else if ("PROJECT".equalsIgnoreCase(req.getTargetType())) {
            ProjectEntity proj = projectRepository.findById(UUID.fromString(req.getTargetId()))
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Dự án"));
            workspaceId = proj.getWorkspaceId();
            targetName = proj.getName();
        } else {
            throw new IllegalArgumentException("Loại mục tiêu rời không hợp lệ");
        }

        // Tìm tất cả Admin của Workspace
        List<UUID> adminIds = workspaceMemberRepository.findAdminUserIdsByWorkspaceId(workspaceId);

        // Đảm bảo không thể tự xin rời nếu là Admin duy nhất
        if (adminIds.contains(user.getId()) && adminIds.size() == 1) {
            throw new IllegalArgumentException("Bạn là Admin duy nhất của Workspace này. Bạn cần chuyển quyền Admin cho thành viên khác trước khi rời!");
        }

        // Tạo bản ghi Yêu cầu rời PENDING
        InvitationEntity leaveReq = new InvitationEntity();
        leaveReq.setTargetType(req.getTargetType().toUpperCase());
        leaveReq.setTargetId(UUID.fromString(req.getTargetId()));
        leaveReq.setInviterId(user.getId());
        leaveReq.setInviteeId(user.getId());
        leaveReq.setRole("MEMBER");
        leaveReq.setType("LEAVE_REQUEST");
        leaveReq.setStatus("PENDING");
        leaveReq.setCreatedAt(LocalDateTime.now());
        leaveReq.setUpdatedAt(LocalDateTime.now());

        InvitationEntity savedReq = invitationRepository.save(leaveReq);

        // Gửi thông báo cho tất cả Admin của Workspace
        String title = "Yêu cầu rời " + ("WORKSPACE".equalsIgnoreCase(req.getTargetType()) ? "Workspace" : "Dự án");
        String content = user.getFullname() + " (" + user.getEmail() + ") đã gửi yêu cầu rời khỏi " +
                ("WORKSPACE".equalsIgnoreCase(req.getTargetType()) ? "Workspace" : "Dự án") +
                " \"" + targetName + "\". Vui lòng xem xét phê duyệt.";

        for (UUID adminId : adminIds) {
            if (!adminId.equals(user.getId())) {
                createNotificationWithInvitation(adminId.toString(), title, content, "LEAVE_REQUEST", savedReq.getId());
            }
        }
    }

    /**
     * Admin duyệt hoặc từ chối yêu cầu rời
     */
    @Transactional
    public NotificationEntity respondLeaveRequest(String notificationId, String action, String adminUserId) {
        NotificationEntity notification = notificationRepository.findById(UUID.fromString(notificationId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));

        if (!notification.getUserId().toString().equals(adminUserId)) {
            throw new IllegalArgumentException("Bạn không có quyền xử lý yêu cầu này");
        }

        if (notification.getInvitationId() == null) {
            throw new IllegalArgumentException("Thông báo không chứa yêu cầu rời hợp lệ");
        }

        InvitationEntity leaveReq = invitationRepository.findById(notification.getInvitationId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rời"));

        if (!"PENDING".equalsIgnoreCase(leaveReq.getStatus())) {
            throw new IllegalArgumentException("Yêu cầu này đã được xử lý trước đó");
        }

        UserEntity requestingUser = userRepository.findById(leaveReq.getInviterId()).orElseThrow();
        String targetName = "";
        UUID workspaceIdToLeave = leaveReq.getTargetId();

        if ("PROJECT".equalsIgnoreCase(leaveReq.getTargetType())) {
            ProjectEntity proj = projectRepository.findById(leaveReq.getTargetId()).orElseThrow();
            targetName = proj.getName();
            workspaceIdToLeave = proj.getWorkspaceId();
        } else {
            WorkspaceEntity ws = workspaceRepository.findById(leaveReq.getTargetId()).orElseThrow();
            targetName = ws.getName();
        }

        if ("APPROVE".equalsIgnoreCase(action)) {
            leaveReq.setStatus("APPROVED");
            leaveReq.setUpdatedAt(LocalDateTime.now());
            invitationRepository.save(leaveReq);

            // Gỡ khỏi Workspace Member
            workspaceMemberRepository.deleteById(new WorkspaceMemberId(workspaceIdToLeave, requestingUser.getId()));

            // Gửi thông báo kết quả cho người dùng
            createNotification(
                    requestingUser.getId().toString(),
                    "Yêu cầu rời đã được duyệt",
                    "Yêu cầu rời " + ("WORKSPACE".equalsIgnoreCase(leaveReq.getTargetType()) ? "Workspace" : "Dự án") +
                            " \"" + targetName + "\" của bạn đã được Admin chấp nhận.",
                    "WORKSPACE"
            );
        } else {
            leaveReq.setStatus("REJECTED_LEAVE");
            leaveReq.setUpdatedAt(LocalDateTime.now());
            invitationRepository.save(leaveReq);

            // Gửi thông báo từ chối cho người dùng
            createNotification(
                    requestingUser.getId().toString(),
                    "Yêu cầu rời bị từ chối",
                    "Yêu cầu rời " + ("WORKSPACE".equalsIgnoreCase(leaveReq.getTargetType()) ? "Workspace" : "Dự án") +
                            " \"" + targetName + "\" của bạn đã bị Admin từ chối.",
                    "WORKSPACE"
            );
        }

        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void inviteUsersBatch(BatchInviteUserRequest req, String inviterId) {
        if (req.getEmails() == null || req.getEmails().isEmpty()) {
            throw new IllegalArgumentException("Danh sách email không được để trống");
        }
        for (String email : req.getEmails()) {
            if (email != null && !email.isBlank()) {
                InviteUserRequest singleReq = new InviteUserRequest();
                singleReq.setTargetType(req.getTargetType());
                singleReq.setTargetId(req.getTargetId());
                singleReq.setRole(req.getRole());
                singleReq.setEmail(email.trim());
                try {
                    inviteUser(singleReq, inviterId);
                } catch (Exception e) {
                    // Tiếp tục xử lý các email còn lại nếu 1 email đã có trong hệ thống hoặc bị trùng
                }
            }
        }
    }
}

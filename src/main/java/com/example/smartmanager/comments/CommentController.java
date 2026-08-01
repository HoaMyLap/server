package com.example.smartmanager.comments;

import com.example.smartmanager.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @PreAuthorize("@securityService.hasTaskRole(#comment.taskId.toString(), 'MEMBER')")
    public ResponseEntity<?> createComment(
            @Valid @RequestBody CommentEntity comment,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            CommentEntity created = commentService.createComment(comment, userPrincipal.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<List<CommentEntity>> getTaskComments(@PathVariable("taskId") String taskId) {
        List<CommentEntity> comments = commentService.getTaskComments(taskId);
        return ResponseEntity.ok(comments);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateComment(
            @PathVariable("id") String commentId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String content = body.get("content");
            CommentEntity updated = commentService.updateComment(commentId, content, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable("id") String commentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            commentService.deleteComment(commentId, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Đã xóa bình luận thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(
            @PathVariable("id") String commentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            CommentEntity updated = commentService.toggleLike(commentId, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/task/{taskId}/viewing")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<?> setViewingDiscussion(
            @PathVariable("taskId") String taskId,
            @RequestParam("viewing") boolean viewing,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            commentService.setViewingDiscussion(taskId, userPrincipal.getId(), viewing);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

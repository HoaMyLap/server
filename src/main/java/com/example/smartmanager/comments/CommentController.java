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
}

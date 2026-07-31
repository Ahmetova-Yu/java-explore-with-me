package ru.practicum.ewm.comment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.service.CommentService;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PrivateCommentController {

    private final CommentService commentService;

    @PostMapping("/events/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody NewCommentDto dto) {
        log.info("POST /users/{}/events/{}/comments", userId, eventId);
        return commentService.addComment(userId, eventId, dto);
    }

    @PatchMapping("/comments/{commentId}")
    public CommentDto updateComment(
            @PathVariable Long userId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentDto dto) {
        log.info("PATCH /users/{}/comments/{}", userId, commentId);
        return commentService.updateComment(userId, commentId, dto);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable Long userId,
            @PathVariable Long commentId) {
        log.info("DELETE /users/{}/comments/{}", userId, commentId);
        commentService.deleteComment(userId, commentId);
    }
}

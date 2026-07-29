package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.util.PaginationUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {
        log.debug("Создание комментария: userId={}, eventId={}", userId, eventId);

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("User with id=%d was not found", userId)));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));

        Comment comment = commentMapper.toEntity(dto, event, author);
        Comment saved = commentRepository.save(comment);
        return commentMapper.toDto(saved);
    }

    public List<CommentDto> getCommentsByEvent(Long eventId, Integer from, Integer size) {
        log.debug("Получение комментариев к событию: eventId={}", eventId);

        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException(String.format("Event with id=%d was not found", eventId));
        }

        Pageable pageable = PaginationUtil.of(from, size);
        return commentRepository.findAllByEventId(eventId, pageable).stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto dto) {
        log.debug("Обновление комментария: userId={}, commentId={}", userId, commentId);

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Comment with id=%d was not found", commentId)));

        comment.setText(dto.getText());
        comment.setUpdatedOn(LocalDateTime.now());

        Comment updated = commentRepository.save(comment);
        return commentMapper.toDto(updated);
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        log.debug("Удаление комментария пользователем: userId={}, commentId={}", userId, commentId);

        if (!commentRepository.existsByIdAndAuthorId(commentId, userId)) {
            throw new NotFoundException(String.format("Comment with id=%d was not found", commentId));
        }

        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.debug("Удаление комментария администратором: commentId={}", commentId);

        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException(String.format("Comment with id=%d was not found", commentId));
        }

        commentRepository.deleteById(commentId);
    }
}

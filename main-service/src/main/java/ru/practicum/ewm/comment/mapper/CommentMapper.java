package ru.practicum.ewm.comment.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.user.dto.UserShortDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class CommentMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;

    public Comment toEntity(NewCommentDto dto, Event event, User author) {
        if (dto == null) return null;
        return Comment.builder()
                .text(dto.getText())
                .event(event)
                .author(author)
                .createdOn(LocalDateTime.now())
                .build();
    }

    public CommentDto toDto(Comment comment) {
        if (comment == null) return null;
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .event(comment.getEvent().getId())
                .author(UserShortDto.builder()
                        .id(comment.getAuthor().getId())
                        .name(comment.getAuthor().getName())
                        .build())
                .createdOn(comment.getCreatedOn().format(FORMATTER))
                .updatedOn(comment.getUpdatedOn() != null ? comment.getUpdatedOn().format(FORMATTER) : null)
                .build();
    }
}

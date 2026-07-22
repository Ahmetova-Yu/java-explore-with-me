package ru.practicum.ewm.request.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.model.Request;

import java.time.format.DateTimeFormatter;

@Component
public class RequestMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public ParticipationRequestDto toDto(Request request) {
        if (request == null) return null;
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .event(request.getEvent().getId())
                .requester(request.getRequester().getId())
                .status(request.getStatus().name())
                .created(request.getCreated().format(FORMATTER))
                .build();
    }
}

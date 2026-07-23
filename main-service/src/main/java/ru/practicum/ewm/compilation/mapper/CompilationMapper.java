package ru.practicum.ewm.compilation.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompilationMapper {

    private final EventMapper eventMapper;

    public CompilationDto toDto(Compilation compilation, Map<Long, Long> viewsMap) {
        if (compilation == null) return null;

        List<EventShortDto> events = compilation.getEvents() != null
                ? compilation.getEvents().stream()
                .map(event -> eventMapper.toShortDto(event, viewsMap.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList())
                : Collections.emptyList();

        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .events(events)
                .build();
    }

    public Compilation toEntity(NewCompilationDto dto) {
        if (dto == null) return null;
        return Compilation.builder()
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .title(dto.getTitle())
                .build();
    }
}

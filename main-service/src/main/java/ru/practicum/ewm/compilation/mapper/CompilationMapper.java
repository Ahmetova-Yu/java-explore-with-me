package ru.practicum.ewm.compilation.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.compilation.dto.CompilationDto;
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
        if (compilation == null) {
            return null;
        }
        Map<Long, Long> safeViewsMap = viewsMap != null ? viewsMap : Collections.emptyMap();

        List<EventShortDto> events = Collections.emptyList();
        if (compilation.getEvents() != null && !compilation.getEvents().isEmpty()) {
            events = compilation.getEvents().stream()
                    .map(event -> {
                        Long views = safeViewsMap.getOrDefault(event.getId(), 0L);
                        return eventMapper.toShortDto(event, views);
                    })
                    .collect(Collectors.toList());
        }

        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned() != null ? compilation.getPinned() : false)
                .title(compilation.getTitle() != null ? compilation.getTitle() : "")
                .events(events)
                .build();
    }
}
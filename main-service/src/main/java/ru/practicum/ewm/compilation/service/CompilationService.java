package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.util.PaginationUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;
    private final StatsClient statsClient;  // ← добавить!

    @Transactional
    public CompilationDto saveCompilation(NewCompilationDto dto) {
        log.debug("Создание подборки: title={}", dto.getTitle());
        Compilation compilation = Compilation.builder()
                .title(dto.getTitle())
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .events(new HashSet<>())
                .build();

        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            List<Event> events = eventRepository.findAllById(dto.getEvents());
            compilation.setEvents(new HashSet<>(events));
        }

        Compilation saved = compilationRepository.save(compilation);
        return compilationMapper.toDto(saved, Collections.emptyMap());
    }

    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest request) {
        log.debug("Обновление подборки: id={}", compId);
        Compilation compilation = getCompilationOrThrow(compId);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            compilation.setTitle(request.getTitle());
        }
        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }
        if (request.getEvents() != null) {
            if (request.getEvents().isEmpty()) {
                compilation.setEvents(new HashSet<>());
            } else {
                List<Event> events = eventRepository.findAllById(request.getEvents());
                compilation.setEvents(new HashSet<>(events));
            }
        }

        Compilation updated = compilationRepository.save(compilation);
        return compilationMapper.toDto(updated, Collections.emptyMap());
    }

    @Transactional
    public void deleteCompilation(Long compId) {
        log.debug("Удаление подборки: id={}", compId);
        Compilation compilation = getCompilationOrThrow(compId);
        compilationRepository.delete(compilation);
    }

    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        log.debug("Получение подборок: pinned={}, from={}, size={}", pinned, from, size);
        Pageable pageable = PaginationUtil.of(from, size);
        List<Compilation> compilations = compilationRepository.findAllWithFilter(pinned, pageable);

        Set<Long> allEventIds = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> viewsMap = getViewsMap(allEventIds);

        return compilations.stream()
                .map(c -> compilationMapper.toDto(c, viewsMap))
                .collect(Collectors.toList());
    }

    public CompilationDto getCompilation(Long compId) {
        log.debug("Получение подборки: id={}", compId);
        Compilation compilation = compilationRepository.findByIdWithEvents(compId);
        if (compilation == null) {
            throw new NotFoundException(String.format("Compilation with id=%d was not found", compId));
        }

        Set<Long> eventIds = compilation.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> viewsMap = getViewsMap(eventIds);

        return compilationMapper.toDto(compilation, viewsMap);
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Compilation with id=%d was not found", compId)));
    }

    private Map<Long, Long> getViewsMap(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = eventIds.stream()
                .map(id -> "/events/" + id)
                .collect(Collectors.toList());

        try {
            LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.now();

            List<ViewStatsDto> stats = statsClient.getStats(start, end, uris, true).getBody();

            if (stats == null) {
                return Collections.emptyMap();
            }

            Map<Long, Long> result = new HashMap<>();
            for (ViewStatsDto stat : stats) {
                try {
                    Long id = Long.parseLong(stat.getUri().replace("/events/", ""));
                    result.put(id, stat.getHits());
                } catch (NumberFormatException ignored) {
                }
            }

            for (Long id : eventIds) {
                result.putIfAbsent(id, 0L);
            }

            return result;
        } catch (Exception e) {
            log.warn("Не удалось получить статистику просмотров для подборок: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
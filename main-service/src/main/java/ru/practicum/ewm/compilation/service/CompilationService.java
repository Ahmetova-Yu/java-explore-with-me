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
import ru.practicum.ewm.util.PaginationUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;

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

        return compilationMapper.toDto(compilation, Collections.emptyMap());
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
        return compilationRepository.findAllWithFilter(pinned, pageable).stream()
                .map(c -> compilationMapper.toDto(c, Collections.emptyMap()))
                .collect(Collectors.toList());
    }

    public CompilationDto getCompilation(Long compId) {
        log.debug("Получение подборки: id={}", compId);
        Compilation compilation = compilationRepository.findByIdWithEvents(compId);
        if (compilation == null) {
            throw new NotFoundException(String.format("Compilation with id=%d was not found", compId));
        }
        return compilationMapper.toDto(compilation, Collections.emptyMap());
    }

    private Compilation getCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Compilation with id=%d was not found", compId)));
    }
}

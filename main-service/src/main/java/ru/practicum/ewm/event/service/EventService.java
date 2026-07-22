package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.enums.State;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecification;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.util.EventSort;
import ru.practicum.ewm.util.PaginationUtil;
import ru.practicum.ewm.util.StateAction;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    // ============================================
    // Private API: события пользователя
    // ============================================

    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        log.debug("Создание события пользователем: userId={}", userId);
        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("User with id=%d was not found", userId)));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException(
                        String.format("Category with id=%d was not found", dto.getCategory())));

        LocalDateTime eventDate = parseDateTime(dto.getEventDate(), "eventDate");
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2).minusMinutes(1))) {
            throw new BadRequestException(
                    "Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate());
        }

        Event event = eventMapper.toEntity(dto, initiator, category);
        event.setState(State.PENDING);
        Event saved = eventRepository.save(event);
        return eventMapper.toFullDto(saved, 0L);
    }

    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        log.debug("Получение событий пользователя: userId={}", userId);
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(String.format("User with id=%d was not found", userId));
        }
        Pageable pageable = PaginationUtil.of(from, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);
        Map<Long, Long> viewsMap = getViewsMap(events);
        return events.stream()
                .map(e -> eventMapper.toShortDto(e, viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        log.debug("Получение события пользователя: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));
        Long views = getViewsForEvent(eventId);
        return eventMapper.toFullDto(event, views);
    }

    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        log.debug("Обновление события пользователем: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));

        if (event.getState() == State.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        applyUserUpdate(event, request);
        Event updated = eventRepository.save(event);
        Long views = getViewsForEvent(eventId);
        return eventMapper.toFullDto(updated, views);
    }

    // ============================================
    // Admin API
    // ============================================

    public List<EventFullDto> getEventsForAdmin(List<Long> users, List<String> states,
                                                List<Long> categories, String rangeStartStr,
                                                String rangeEndStr, Integer from, Integer size) {
        log.debug("Admin: поиск событий");
        List<State> stateEnums = states != null ? states.stream().map(State::valueOf).toList() : null;
        LocalDateTime rangeStart = rangeStartStr != null ? parseDateTime(rangeStartStr, "rangeStart") : null;
        LocalDateTime rangeEnd = rangeEndStr != null ? parseDateTime(rangeEndStr, "rangeEnd") : null;

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Incorrectly made request: rangeStart must be before rangeEnd");
        }

        Specification<Event> spec = EventSpecification.forAdmin(users, stateEnums, categories, rangeStart, rangeEnd);
        Pageable pageable = PaginationUtil.of(from, size);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();
        Map<Long, Long> viewsMap = getViewsMap(events);
        return events.stream()
                .map(e -> eventMapper.toFullDto(e, viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        log.debug("Admin: обновление события: id={}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));

        applyAdminUpdate(event, request);
        Event updated = eventRepository.save(event);
        Long views = getViewsForEvent(eventId);
        return eventMapper.toFullDto(updated, views);
    }

    // ============================================
    // Public API
    // ============================================

    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               String rangeStartStr, String rangeEndStr,
                                               Boolean onlyAvailable, String sortStr,
                                               Integer from, Integer size,
                                               HttpServletRequest request) {
        log.debug("Public: поиск событий");
        LocalDateTime rangeStart = rangeStartStr != null
                ? parseDateTime(rangeStartStr, "rangeStart")
                : LocalDateTime.now();
        LocalDateTime rangeEnd = rangeEndStr != null
                ? parseDateTime(rangeEndStr, "rangeEnd")
                : null;

        if (rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Incorrectly made request: rangeStart must be before rangeEnd");
        }

        Specification<Event> spec = EventSpecification.forPublic(text, categories, paid, rangeStart, rangeEnd, onlyAvailable);
        Pageable pageable = buildPageable(from, size, sortStr);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();
        Map<Long, Long> viewsMap = getViewsMap(events);

        saveHit(request);

        return events.stream()
                .map(e -> eventMapper.toShortDto(e, viewsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    public EventFullDto getPublicEvent(Long id, HttpServletRequest request) {
        log.debug("Public: получение события: id={}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", id)));

        if (event.getState() != State.PUBLISHED) {
            throw new NotFoundException(String.format("Event with id=%d was not found", id));
        }

        Long views = getViewsForEvent(id);
        saveHit(request);
        return eventMapper.toFullDto(event, views);
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private void applyUserUpdate(Event event, UpdateEventUserRequest request) {
        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getCategory() != null) {
            Category category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Category with id=%d was not found", request.getCategory())));
            event.setCategory(category);
        }
        if (request.getEventDate() != null) {
            LocalDateTime newDate = parseDateTime(request.getEventDate(), "eventDate");
            if (newDate.isBefore(LocalDateTime.now().plusHours(2).minusMinutes(1))) {
                throw new BadRequestException(
                        "Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + request.getEventDate());
            }
            event.setEventDate(newDate);
        }
        if (request.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());

        if (request.getStateAction() != null) {
            StateAction action = parseStateAction(request.getStateAction());
            switch (action) {
                case SEND_TO_REVIEW -> event.setState(State.PENDING);
                case CANCEL_REVIEW -> event.setState(State.CANCELED);
            }
        }
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest request) {
        if (request.getStateAction() != null) {
            StateAction action = parseStateAction(request.getStateAction());
            switch (action) {
                case PUBLISH_EVENT -> {
                    if (event.getState() != State.PENDING) {
                        throw new ConflictException(
                                "Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(State.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                }
                case REJECT_EVENT -> {
                    if (event.getState() == State.PUBLISHED) {
                        throw new ConflictException(
                                "Cannot reject the event because it's already published");
                    }
                    event.setState(State.CANCELED);
                }
            }
        }

        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getCategory() != null) {
            Category category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Category with id=%d was not found", request.getCategory())));
            event.setCategory(category);
        }
        if (request.getEventDate() != null) {
            LocalDateTime newDate = parseDateTime(request.getEventDate(), "eventDate");
            if (newDate.isBefore(LocalDateTime.now().plusHours(1).minusMinutes(1))) {
                throw new BadRequestException(
                        "Field: eventDate. Error: дата должна быть не ранее чем за час от текущего момента. Value: " + request.getEventDate());
            }
            event.setEventDate(newDate);
        }
        if (request.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());
    }

    private Pageable buildPageable(Integer from, Integer size, String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return PaginationUtil.of(from, size, Sort.by(Sort.Direction.ASC, "eventDate"));
        }
        EventSort sort = EventSort.valueOf(sortStr);
        if (sort == EventSort.EVENT_DATE) {
            return PaginationUtil.of(from, size, Sort.by(Sort.Direction.ASC, "eventDate"));
        } else {
            return PaginationUtil.of(from, size, Sort.by(Sort.Direction.DESC, "eventDate"));
        }
    }

    private Map<Long, Long> getViewsMap(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());
        try {
            LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.now();
            List<ViewStatsDto> stats = statsClient.getStats(start, end, uris, true).getBody();
            if (stats == null) return Collections.emptyMap();
            Map<Long, Long> result = new HashMap<>();
            for (ViewStatsDto stat : stats) {
                try {
                    Long id = Long.parseLong(stat.getUri().replace("/events/", ""));
                    result.put(id, stat.getHits());
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Не удалось получить статистику просмотров: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Long getViewsForEvent(Long eventId) {
        try {
            List<String> uris = List.of("/events/" + eventId);
            LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.now();
            List<ViewStatsDto> stats = statsClient.getStats(start, end, uris, true).getBody();
            if (stats != null && !stats.isEmpty()) {
                return stats.get(0).getHits();
            }
        } catch (Exception e) {
            log.warn("Не удалось получить статистику для события {}: {}", eventId, e.getMessage());
        }
        return 0L;
    }

    private void saveHit(HttpServletRequest request) {
        try {
            EndpointHitDto hit = EndpointHitDto.builder()
                    .app("ewm-main-service")
                    .uri(request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .timestamp(LocalDateTime.now())
                    .build();
            statsClient.saveHit(hit);
        } catch (Exception e) {
            log.warn("Не удалось сохранить хит в статистику: {}", e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr, String fieldName) {
        try {
            return LocalDateTime.parse(dateTimeStr, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                    String.format("Field: %s. Error: Incorrect date format. Value: %s", fieldName, dateTimeStr));
        }
    }

    private StateAction parseStateAction(String stateActionStr) {
        try {
            return StateAction.valueOf(stateActionStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    String.format("Field: stateAction. Error: Unknown state action. Value: %s", stateActionStr));
        }
    }
}

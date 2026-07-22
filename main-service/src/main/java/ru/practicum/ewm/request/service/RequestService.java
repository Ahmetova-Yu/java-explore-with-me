package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.enums.State;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.enums.RequestStatus;
import ru.practicum.ewm.request.mapper.RequestMapper;
import ru.practicum.ewm.request.model.Request;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {
    private final RequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RequestMapper requestMapper;

    @Transactional
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        log.debug("Создание заявки: userId={}, eventId={}", userId, eventId);

        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("User with id=%d was not found", userId)));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));

        if (event.getState() != State.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot participate in their own event");
        }

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request already exists");
        }

        if (event.getParticipantLimit() != 0 && !event.getRequestModeration()) {
            long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("Participant limit has been reached");
            }
        }

        Request request = Request.builder()
                .event(event)
                .requester(requester)
                .created(LocalDateTime.now())
                .status((event.getParticipantLimit() == 0 || !event.getRequestModeration())
                        ? RequestStatus.CONFIRMED
                        : RequestStatus.PENDING)
                .build();

        Request saved = requestRepository.save(request);

        if (saved.getStatus() == RequestStatus.CONFIRMED) {
            updateConfirmedRequests(event);
        }

        return requestMapper.toDto(saved);
    }

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.debug("Получение заявок пользователя: userId={}", userId);
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException(String.format("User with id=%d was not found", userId));
        }
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.debug("Отмена заявки: userId={}, requestId={}", userId, requestId);
        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Request with id=%d was not found", requestId)));
        request.setStatus(RequestStatus.CANCELED);
        Request saved = requestRepository.save(request);
        updateConfirmedRequests(request.getEvent());
        return requestMapper.toDto(saved);
    }

    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.debug("Получение заявок на событие: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));
        return requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest request) {
        log.debug("Изменение статуса заявок: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with id=%d was not found", eventId)));

        RequestStatus newStatus = RequestStatus.valueOf(request.getStatus());
        List<Request> requests = requestRepository.findAllByIdIn(request.getRequestIds());

        List<Request> confirmed = new ArrayList<>();
        List<Request> rejected = new ArrayList<>();

        long currentConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        int limit = event.getParticipantLimit();

        for (Request r : requests) {
            if (!r.getEvent().getId().equals(eventId)) continue;
            if (r.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }

            if (newStatus == RequestStatus.CONFIRMED) {
                if (limit != 0 && currentConfirmed >= limit) {
                    throw new ConflictException("The participant limit has been reached");
                }
                r.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(r);
                currentConfirmed++;

                if (limit != 0 && currentConfirmed >= limit) {
                    // Отклоняем все оставшиеся PENDING заявки
                    List<Request> pending = requestRepository.findAllByEventIdAndStatus(eventId, RequestStatus.PENDING);
                    for (Request p : pending) {
                        if (!request.getRequestIds().contains(p.getId())) {
                            p.setStatus(RequestStatus.REJECTED);
                            rejected.add(p);
                        }
                    }
                    requestRepository.saveAll(pending);
                }
            } else {
                r.setStatus(RequestStatus.REJECTED);
                rejected.add(r);
            }
        }

        requestRepository.saveAll(requests);
        updateConfirmedRequests(event);

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed.stream().map(requestMapper::toDto).collect(Collectors.toList()))
                .rejectedRequests(rejected.stream().map(requestMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    private void updateConfirmedRequests(Event event) {
        long count = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        event.setConfirmedRequests(count);
        eventRepository.save(event);
    }
}

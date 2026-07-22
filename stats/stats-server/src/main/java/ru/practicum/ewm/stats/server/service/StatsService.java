package ru.practicum.ewm.stats.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;
import ru.practicum.ewm.stats.server.mapper.StatsMapper;
import ru.practicum.ewm.stats.server.model.EndpointHit;
import ru.practicum.ewm.stats.server.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatsService {
    private final StatsRepository repository;
    private final StatsMapper mapper;

    public EndpointHitDto saveHit(EndpointHitDto hitDto) {
        log.debug("Сохранение хита: app={}, uri={}, ip={}",
                hitDto.getApp(), hitDto.getUri(), hitDto.getIp());

        EndpointHit hit = mapper.toEntity(hitDto);
        hit.setTimestamp(hit.getTimestamp() != null ? hit.getTimestamp() : LocalDateTime.now());
        return mapper.toDto(repository.save(hit));
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end,
                                       List<String> uris, Boolean unique) {
        log.debug("Запрос статистики: start={}, end={}, unique={}, uris={}", start, end, unique, uris);

        boolean hasUris = uris != null && !uris.isEmpty();

        if (Boolean.TRUE.equals(unique)) {
            return hasUris ? repository.findUniqueStatsByUris(start, end, uris)
                    : repository.findUniqueStats(start, end);
        }
        return hasUris ? repository.findStatsByUris(start, end, uris)
                : repository.findStats(start, end);
    }
}

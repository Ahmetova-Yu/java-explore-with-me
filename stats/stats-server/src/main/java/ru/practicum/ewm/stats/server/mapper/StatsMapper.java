package ru.practicum.ewm.stats.server.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.server.model.EndpointHit;

@Component
public class StatsMapper {

    public EndpointHit toEntity(EndpointHitDto dto) {
        if (dto == null) {
            return null;
        }
        return EndpointHit.builder()
                .id(dto.getId())
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(dto.getTimestamp())
                .build();
    }

    public EndpointHitDto toDto(EndpointHit entity) {
        if (entity == null) {
            return null;
        }
        return EndpointHitDto.builder()
                .id(entity.getId())
                .app(entity.getApp())
                .uri(entity.getUri())
                .ip(entity.getIp())
                .timestamp(entity.getTimestamp())
                .build();
    }
}

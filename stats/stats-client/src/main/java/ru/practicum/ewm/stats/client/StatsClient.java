package ru.practicum.ewm.stats.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class StatsClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestTemplate restTemplate;

    public StatsClient(@Value("${stats-server.url:http://localhost:9090}") String serverUrl,
                       RestTemplateBuilder builder) {
        this.restTemplate = builder
                .uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl))
                .build();
    }

    public ResponseEntity<Object> saveHit(EndpointHitDto hitDto) {
        return restTemplate.postForEntity("/hit", hitDto, Object.class);
    }

    public ResponseEntity<List<ViewStatsDto>> getStats(LocalDateTime start, LocalDateTime end,
                                                       List<String> uris, Boolean unique) {
        StringBuilder url = new StringBuilder("/stats?start=")
                .append(start.format(FORMATTER))
                .append("&end=").append(end.format(FORMATTER))
                .append("&unique=").append(unique);
        if (uris != null && !uris.isEmpty()) {
            for (String uri : uris) {
                url.append("&uris=").append(uri);
            }
        }
        ViewStatsDto[] response = restTemplate.getForObject(url.toString(), ViewStatsDto[].class);
        return ResponseEntity.ok(response != null ? Arrays.asList(response) : List.of());
    }
}

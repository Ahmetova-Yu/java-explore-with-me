package ru.practicum.ewm.stats.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.ewm.stats.dto.EndpointHitDto;

@Service
public class StatsClient {
    private final org.springframework.web.client.RestTemplate restTemplate;

    @Autowired
    public StatsClient(@Value("${stats-server.url:http://localhost:9090}") String serverUrl,
                       RestTemplateBuilder builder) {
        this.restTemplate = builder
                .uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl))
                .build();
    }

    public ResponseEntity<Object> saveHit(EndpointHitDto hitDto) {
        return restTemplate.postForEntity("/hit", hitDto, Object.class);
    }
}

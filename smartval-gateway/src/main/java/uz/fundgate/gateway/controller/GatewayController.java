package uz.fundgate.gateway.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Gateway", description = "API Gateway routing")
@Slf4j
public class GatewayController {

    private final RestTemplate restTemplate;

    @Value("${services.fundgate.url:http://localhost:8081}")
    private String fundgateUrl;

    @Value("${services.valuation.url:http://localhost:8082}")
    private String valuationUrl;

    @Value("${services.submission.url:http://localhost:8083}")
    private String submissionUrl;

    @Value("${services.email.url:http://localhost:8084}")
    private String emailUrl;

    @Value("${services.chat.url:http://localhost:8085}")
    private String chatUrl;

    @Value("${services.crm.url:http://localhost:8086}")
    private String crmUrl;

    @Value("${services.spam.url:http://localhost:8087}")
    private String spamUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(5))
                .build();
    }
}

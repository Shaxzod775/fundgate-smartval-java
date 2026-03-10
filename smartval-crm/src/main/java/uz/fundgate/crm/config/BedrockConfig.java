package uz.fundgate.crm.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * AWS Bedrock client configuration for CRM AI Service.
 * Creates the BedrockRuntimeClient for Claude model invocations with tool use.
 *
 * Ported from Python: AWS Bedrock configuration in main.py
 */
@Slf4j
@Getter
@Configuration
public class BedrockConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.fallback-model-id:us.anthropic.claude-3-5-haiku-20241022-v1:0}")
    private String fallbackModelId;

    @Value("${aws.bedrock.max-tokens:4096}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.7}")
    private double temperature;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("Initializing AWS Bedrock Runtime Client for CRM AI in region: {}", awsRegion);

        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("AWS Bedrock Runtime Client initialized. Model: {}, MaxTokens: {}", modelId, maxTokens);
        return client;
    }
}

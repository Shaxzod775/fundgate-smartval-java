package uz.fundgate.fundgate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * AWS Bedrock client bean configuration.
 * Creates the BedrockRuntimeClient for Claude model invocations.
 */
@Slf4j
@Configuration
public class BedrockConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-3-5-haiku-20241022-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:8192}")
    private int maxTokens;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("Initializing AWS Bedrock Runtime Client in region: {}", awsRegion);

        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("AWS Bedrock Runtime Client initialized. Model: {}, MaxTokens: {}", modelId, maxTokens);
        return client;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}

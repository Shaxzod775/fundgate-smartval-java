package uz.fundgate.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SmartVal Chat Service - Spring Boot Application.
 *
 * ChatKit backend providing AI-powered chat with document generation.
 * Features:
 * - Multi-model AI chat (Claude via Bedrock) with SSE streaming
 * - Document generation (Word, Excel, PowerPoint) via Apache POI
 * - Pitch deck PDF analysis using Claude vision
 * - Audio transcription placeholder
 * - Conversation context management
 */
@SpringBootApplication(scanBasePackages = {"uz.fundgate.chat", "uz.fundgate.common"})
@EnableAsync
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}

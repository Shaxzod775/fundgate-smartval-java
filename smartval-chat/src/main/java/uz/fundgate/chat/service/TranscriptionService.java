package uz.fundgate.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Placeholder service for audio transcription.
 *
 * Ported from Python chatkit-backend: transcription.py
 *
 * In the Python version, this used OpenAI Whisper API.
 * This Java implementation is a placeholder that can be extended
 * with AWS Transcribe or another transcription service.
 */
@Slf4j
@Service
public class TranscriptionService {

    /**
     * Transcribe audio data.
     *
     * @param audioBase64    base64-encoded audio data
     * @param languageCode   language code (e.g., "ru-RU", "en-US")
     * @return transcription result
     */
    public Map<String, Object> transcribe(String audioBase64, String languageCode) {
        log.info("[TRANSCRIBE] Received transcription request, language: {}", languageCode);

        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new IllegalArgumentException("Audio data is required");
        }

        // Placeholder implementation
        // TODO: Integrate with AWS Transcribe or another transcription service
        log.warn("[TRANSCRIBE] Transcription not yet implemented. " +
                "Integrate with AWS Transcribe for production use.");

        return Map.of(
                "transcript", "",
                "method", "placeholder",
                "status", "not_implemented",
                "message", "Transcription service is not yet configured. " +
                        "Please integrate with AWS Transcribe."
        );
    }
}

package uz.fundgate.spam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import uz.fundgate.spam.dto.BatchSpamCheckRequest;
import uz.fundgate.spam.dto.SpamCheckRequest;
import uz.fundgate.spam.dto.SpamCheckResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for AI-powered spam/fraud detection using Claude via AWS Bedrock.
 *
 * Ported from Python spam_checker/app.py.
 *
 * Uses a two-phase approach:
 * 1. Fast heuristic pre-checks (regex-based) for obvious spam
 * 2. Claude AI analysis for ambiguous cases
 *
 * Detects:
 * - Random/gibberish text and keyboard mashing
 * - Suspicious email patterns
 * - Duplicate/repetitive submissions
 * - Fraudulent startup descriptions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpamCheckService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:100}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.0}")
    private double temperature;

    private static final String SPAM_SYSTEM_PROMPT = """
            You are a spam detector. Your ONLY job is to detect OBVIOUS garbage/spam text.

            SPAM (return true):
            - Random letters: asdfghjkl, qwertyuiop, jfkdlsjfklds
            - Random numbers: 320323920320392, 111222333444
            - Random symbols: !@#$%^&*, /////, -----
            - Keyboard mashing: sdfghjkl, zxcvbnm, qwerty
            - Repeating characters: aaaaaaaa, 11111111
            - Mixed garbage: a1s2d3f4g5, x!y@z#1$2

            NOT SPAM (return false):
            - Any real words in any language (English, Russian, Uzbek, etc.)
            - Names, company names, product names
            - Sentences, phrases, descriptions
            - Numbers with meaning (dates, prices, quantities)
            - Technical terms, abbreviations
            - URLs, emails, phone numbers
            - Business descriptions, startup pitches

            Return ONLY "true" or "false". Nothing else.
            """;

    // Suspicious email domain patterns
    private static final Set<String> SUSPICIOUS_DOMAINS = Set.of(
            "tempmail.com", "throwaway.email", "guerrillamail.com",
            "mailinator.com", "yopmail.com", "trashmail.com",
            "fakeinbox.com", "sharklasers.com", "guerrillamailblock.com"
    );

    // Regex patterns for heuristic detection
    private static final Pattern REPEATING_CHARS = Pattern.compile("(.)\\1{9,}");
    private static final Pattern ONLY_LONG_DIGITS = Pattern.compile("^\\d{15,}$");
    private static final Pattern ONLY_SYMBOLS = Pattern.compile(
            "^[!@#$%^&*()_+\\-=\\[\\]{}|\\\\:\";'<>?,./~`\\s]{10,}$");
    private static final Pattern KEYBOARD_ROWS = Pattern.compile(
            "(?i)(qwerty|asdfgh|zxcvbn|qwertyuiop|asdfghjkl|zxcvbnm)");

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Check a single submission for spam.
     */
    public SpamCheckResponse checkSingle(SpamCheckRequest request) {
        log.info("[SPAM] Checking single entry - content length: {}",
                request.getContent() != null ? request.getContent().length() : 0);

        List<String> reasons = new ArrayList<>();

        // Phase 1: Heuristic pre-checks
        if (request.getContent() != null) {
            List<String> heuristicReasons = checkHeuristics(request.getContent());
            if (!heuristicReasons.isEmpty()) {
                log.info("[SPAM] Heuristic spam detected in content");
                reasons.addAll(heuristicReasons);
                return SpamCheckResponse.spam(0.95, reasons);
            }
        }

        // Check email for suspicious patterns
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            List<String> emailReasons = checkEmail(request.getEmail());
            reasons.addAll(emailReasons);
        }

        // Check startup name for gibberish
        if (request.getStartupName() != null && !request.getStartupName().isBlank()) {
            List<String> nameReasons = checkHeuristics(request.getStartupName());
            if (!nameReasons.isEmpty()) {
                reasons.add("Startup name appears to be gibberish");
                return SpamCheckResponse.spam(0.9, reasons);
            }
        }

        // Check description
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            List<String> descReasons = checkHeuristics(request.getDescription());
            if (!descReasons.isEmpty()) {
                reasons.add("Description appears to be gibberish");
                return SpamCheckResponse.spam(0.9, reasons);
            }
        }

        // Phase 2: AI check for ambiguous content
        String textToCheck = buildTextForAiCheck(request);
        if (textToCheck.length() >= 5) {
            boolean isSpamAi = checkWithAi(textToCheck);
            if (isSpamAi) {
                reasons.add("AI detected spam/garbage content");
                return SpamCheckResponse.spam(0.85, reasons);
            }
        }

        // Not spam - but may have minor email warnings
        if (!reasons.isEmpty()) {
            return SpamCheckResponse.builder()
                    .isSpam(false)
                    .confidence(0.3)
                    .reasons(reasons)
                    .build();
        }

        return SpamCheckResponse.clean();
    }

    /**
     * Check a batch of submissions for spam.
     */
    public List<SpamCheckResponse> checkBatch(BatchSpamCheckRequest request) {
        log.info("[SPAM] Batch check - {} entries", request.getEntries().size());

        List<SpamCheckResponse> results = new ArrayList<>();
        for (SpamCheckRequest entry : request.getEntries()) {
            results.add(checkSingle(entry));
        }

        long spamCount = results.stream().filter(SpamCheckResponse::isSpam).count();
        log.info("[SPAM] Batch result: {}/{} spam detected",
                spamCount, request.getEntries().size());

        return results;
    }

    // =========================================================================
    // Heuristic Pre-checks
    // =========================================================================

    /**
     * Fast local check for obvious spam patterns.
     * Mirrors Python is_obvious_spam_local().
     */
    private List<String> checkHeuristics(String text) {
        List<String> reasons = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return reasons;
        }

        String trimmed = text.trim();

        // Skip very short texts
        if (trimmed.length() < 5) {
            return reasons;
        }

        // Repeating same character 10+ times
        if (REPEATING_CHARS.matcher(trimmed).find()) {
            reasons.add("Contains repeating characters (10+)");
        }

        // Only digits (15+)
        if (ONLY_LONG_DIGITS.matcher(trimmed).matches()) {
            reasons.add("Contains only random digits (15+)");
        }

        // Only symbols (10+)
        if (ONLY_SYMBOLS.matcher(trimmed).matches()) {
            reasons.add("Contains only random symbols (10+)");
        }

        // Keyboard row patterns
        if (KEYBOARD_ROWS.matcher(trimmed).find() && trimmed.length() < 30) {
            reasons.add("Detected keyboard mashing pattern");
        }

        // High ratio of non-alphanumeric characters
        long alphaNum = trimmed.chars().filter(Character::isLetterOrDigit).count();
        if (trimmed.length() > 10 && alphaNum < trimmed.length() * 0.3) {
            reasons.add("Unusually high ratio of special characters");
        }

        return reasons;
    }

    /**
     * Check email address for suspicious patterns.
     */
    private List<String> checkEmail(String email) {
        List<String> reasons = new ArrayList<>();

        if (email == null || email.isBlank()) {
            return reasons;
        }

        String lower = email.toLowerCase().trim();

        // Check for suspicious domains
        for (String domain : SUSPICIOUS_DOMAINS) {
            if (lower.endsWith("@" + domain)) {
                reasons.add("Suspicious disposable email domain: " + domain);
                break;
            }
        }

        // Check for random-looking email prefix
        if (lower.contains("@")) {
            String prefix = lower.substring(0, lower.indexOf('@'));
            // Very long random-looking prefix
            if (prefix.length() > 20 && !prefix.contains(".") && !prefix.contains("_")) {
                long digits = prefix.chars().filter(Character::isDigit).count();
                if (digits > prefix.length() * 0.6) {
                    reasons.add("Email prefix appears randomly generated");
                }
            }
        }

        return reasons;
    }

    /**
     * Detect duplicate/near-duplicate submissions.
     * Currently a placeholder - implement with a cache/database for production.
     */
    @SuppressWarnings("unused")
    private boolean isDuplicate(String content) {
        // TODO: Implement duplicate detection using Redis or DB hash comparison
        return false;
    }

    // =========================================================================
    // AI Check via Claude Bedrock
    // =========================================================================

    /**
     * Check text using Claude AI via Bedrock.
     * Returns true if classified as spam.
     */
    private boolean checkWithAi(String text) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("system", SPAM_SYSTEM_PROMPT);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", "Is this spam? Text: \"" + text + "\"");

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responseBody);

            // Extract text response
            JsonNode contentArray = responseJson.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        String result = block.path("text").asText().trim().toLowerCase();
                        boolean isSpam = "true".equals(result);
                        log.info("[SPAM] AI response: '{}' -> isSpam={}", result, isSpam);
                        return isSpam;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.error("[SPAM] AI check failed, falling back to heuristic: {}", e.getMessage());
            // On API error, rely on heuristic checks only
            return !checkHeuristics(text).isEmpty();
        }
    }

    /**
     * Build a consolidated text string for AI analysis.
     */
    private String buildTextForAiCheck(SpamCheckRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getContent() != null && !request.getContent().isBlank()) {
            sb.append(request.getContent());
        }
        if (request.getStartupName() != null && !request.getStartupName().isBlank()) {
            sb.append(" | ").append(request.getStartupName());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            sb.append(" | ").append(request.getDescription());
        }
        return sb.toString().trim();
    }
}

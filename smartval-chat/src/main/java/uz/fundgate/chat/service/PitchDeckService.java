package uz.fundgate.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Service for analyzing pitch deck PDFs using Claude Vision via AWS Bedrock.
 *
 * Ported from Python chatkit-backend: pitch_deck.py + claude_document_agent.py
 *
 * Converts PDF pages to images, then sends them to Claude for analysis.
 * Extracts startup information for FundGate form auto-fill.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PitchDeckService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:4096}")
    private int maxTokens;

    @Value("${valuation.pdf.max-pages:20}")
    private int maxPages;

    @Value("${valuation.pdf.image-dpi:150}")
    private int imageDpi;

    private static final String PITCH_DECK_SYSTEM_PROMPT = """
            You are an expert pitch deck analyst for FundGate, a startup investment platform.
            Analyze the pitch deck slides and extract structured information about the startup.

            Extract the following fields for FundGate form auto-fill:

            stepA (Startup Info):
            - name: Startup/company name
            - industry: Industry/sector
            - stage: Development stage (idea, mvp, growth, scale)
            - description: Brief description (max 500 chars)
            - revenueFirstMonth: Monthly revenue in USD
            - userCount: Number of users/customers
            - employees: Number of employees
            - investmentAmount: Investment seeking in USD

            stepB (Founder Info):
            - firstName: Founder first name
            - lastName: Founder last name
            - email: Contact email
            - website: Company website

            Respond in JSON format with:
            {
              "is_startup": true/false,
              "confidence": 0.0-1.0,
              "extracted_data": { "stepA": {...}, "stepB": {...} },
              "missing_fields": ["field1", "field2"],
              "summary": "Brief analysis summary"
            }
            """;

    /**
     * Analyze a pitch deck PDF and extract startup information.
     *
     * @param pdfBytes raw PDF file bytes
     * @param fileName original file name
     * @return map containing analysis results
     */
    public Map<String, Object> analyzePitchDeck(byte[] pdfBytes, String fileName) {
        log.info("[PITCH-DECK] Analyzing: {}, size: {} bytes", fileName, pdfBytes.length);

        try {
            // Convert PDF pages to base64-encoded images
            List<String> pageImages = convertPdfToImages(pdfBytes);
            log.info("[PITCH-DECK] Converted {} pages to images", pageImages.size());

            if (pageImages.isEmpty()) {
                return createErrorResult("Failed to convert PDF to images");
            }

            // Build Claude request with vision (image content blocks)
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", 0.3);
            requestBody.put("system", PITCH_DECK_SYSTEM_PROMPT);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");

            // Add each page image
            for (int i = 0; i < pageImages.size(); i++) {
                ObjectNode imageBlock = content.addObject();
                imageBlock.put("type", "image");
                ObjectNode source = imageBlock.putObject("source");
                source.put("type", "base64");
                source.put("media_type", "image/png");
                source.put("data", pageImages.get(i));
            }

            // Add text prompt
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", "Analyze this pitch deck and extract startup information for FundGate. " +
                    "The document is: " + fileName + ". Respond in JSON format.");

            // Invoke Claude
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
            String analysisText = "";
            JsonNode contentArray = responseJson.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        analysisText += block.path("text").asText();
                    }
                }
            }

            // Parse JSON from response
            return parseAnalysisResponse(analysisText);

        } catch (Exception e) {
            log.error("[PITCH-DECK] Analysis failed: {}", e.getMessage(), e);
            return createErrorResult("Analysis failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Convert PDF pages to base64-encoded PNG images.
     */
    private List<String> convertPdfToImages(byte[] pdfBytes) {
        List<String> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, imageDpi, ImageType.RGB);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                images.add(base64);
            }

        } catch (IOException e) {
            log.error("[PITCH-DECK] PDF to image conversion failed: {}", e.getMessage(), e);
        }

        return images;
    }

    /**
     * Parse Claude's analysis response, extracting JSON from the text.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAnalysisResponse(String responseText) {
        try {
            // Try to extract JSON from the response
            String jsonStr = responseText;

            // Handle markdown code blocks
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }

            return objectMapper.readValue(jsonStr.trim(), Map.class);

        } catch (Exception e) {
            log.warn("[PITCH-DECK] Failed to parse JSON response, returning raw text. Error: {}", e.getMessage());

            Map<String, Object> result = new HashMap<>();
            result.put("is_startup", false);
            result.put("confidence", 0.0);
            result.put("extracted_data", Map.of());
            result.put("missing_fields", List.of());
            result.put("summary", responseText);
            return result;
        }
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("is_startup", false);
        result.put("confidence", 0.0);
        result.put("extracted_data", Map.of());
        result.put("missing_fields", List.of());
        result.put("summary", "");
        result.put("error", errorMessage);
        return result;
    }
}

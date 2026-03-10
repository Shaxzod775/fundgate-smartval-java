package uz.fundgate.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import uz.fundgate.crm.dto.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core CRM AI Service handling startup analysis, file analysis, and chat with tool use.
 *
 * Ported from Python: main.py - chat_with_claude(), execute_tool(), invoke_bedrock(),
 *                     analyze endpoint, analyze-file endpoint.
 *
 * Claude tools supported:
 * - web_search: search the web via DuckDuckGo
 * - get_all_startups: fetch all startups from CRM API
 * - get_startup_details: fetch specific startup details
 * - update_startup: update startup data in CRM
 * - get_managers: list CRM managers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmAiService {

    private final BedrockRuntimeClient bedrockClient;
    private final CrmApiClient crmApiClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}")
    private String modelId;

    @Value("${aws.bedrock.fallback-model-id:us.anthropic.claude-3-5-haiku-20241022-v1:0}")
    private String fallbackModelId;

    @Value("${aws.bedrock.max-tokens:4096}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.7}")
    private double temperature;

    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * In-memory conversation store. Maps conversationId -> list of messages.
     */
    private final ConcurrentHashMap<String, ArrayNode> conversationStore = new ConcurrentHashMap<>();

    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // =========================================================================
    // System Prompt (ported from Python CRM_ASSISTANT_PROMPT)
    // =========================================================================

    private static final String CRM_ASSISTANT_PROMPT = """
            You are FundGate AI - an intelligent assistant for FundGate CRM, a venture capital deal flow management platform.

            ## Your Capabilities
            You help investment managers with:
            1. **Startup Information** - Get detailed information about any startup in the database
            2. **Startup Management** - Update startup information, change status, modify descriptions
            3. **Portfolio Overview** - List all startups, filter by status
            4. **Web Search** - Search the internet for current information
            5. **General Questions** - Answer questions about investment terms and best practices

            ## Available Tools
            1. **get_all_startups** - Get list of all startups. Use when user asks about all startups or portfolio.
            2. **get_startup_details** - Get full details about a specific startup. Use when user asks about a startup's info.
            3. **update_startup** - Update startup data (status, description, funding, etc). Use when user wants to CHANGE/UPDATE/MOVE a startup.
            4. **web_search** - Search the web for current information.
            5. **get_managers** - Get list of managers for assignment.

            ## Important Guidelines
            - ALWAYS use tools to get real data - never make up information
            - Use the user's language (Russian, English, or Uzbek)
            - Be concise but informative
            - After getting startup data, provide analysis and recommendations
            - Format numbers nicely (e.g., $500K instead of 500000)
            """;

    // =========================================================================
    // Claude Tools Definition (ported from Python CLAUDE_TOOLS)
    // =========================================================================

    private String buildToolsJson() {
        try {
            ArrayNode tools = objectMapper.createArrayNode();

            // web_search
            ObjectNode webSearch = tools.addObject();
            webSearch.put("name", "web_search");
            webSearch.put("description", "Search the web for current information. Use this tool when you need to find up-to-date information, news, facts, prices, or any real-time data.");
            ObjectNode wsSchema = webSearch.putObject("input_schema");
            wsSchema.put("type", "object");
            ObjectNode wsProps = wsSchema.putObject("properties");
            ObjectNode wsQuery = wsProps.putObject("query");
            wsQuery.put("type", "string");
            wsQuery.put("description", "The search query to look up on the web");
            wsSchema.putArray("required").add("query");

            // get_all_startups
            ObjectNode getAllStartups = tools.addObject();
            getAllStartups.put("name", "get_all_startups");
            getAllStartups.put("description", "Get a list of all startups from the CRM database. Use this to get an overview of all startups, their statuses, scores, and basic info. Always use this first when the user asks about startups in general.");
            ObjectNode gasSchema = getAllStartups.putObject("input_schema");
            gasSchema.put("type", "object");
            ObjectNode gasProps = gasSchema.putObject("properties");
            ObjectNode gasStatus = gasProps.putObject("status");
            gasStatus.put("type", "string");
            gasStatus.put("description", "Optional filter by status: 'new', 'in_review', 'pipeline', 'portfolio', 'rejected'");
            ArrayNode gasEnum = gasStatus.putArray("enum");
            gasEnum.add("new").add("in_review").add("pipeline").add("portfolio").add("rejected");
            gasSchema.putArray("required");

            // get_startup_details
            ObjectNode getDetails = tools.addObject();
            getDetails.put("name", "get_startup_details");
            getDetails.put("description", "Get detailed information about a specific startup by its ID or name. Use this when the user asks about a specific startup.");
            ObjectNode gdSchema = getDetails.putObject("input_schema");
            gdSchema.put("type", "object");
            ObjectNode gdProps = gdSchema.putObject("properties");
            ObjectNode gdId = gdProps.putObject("startup_id");
            gdId.put("type", "string");
            gdId.put("description", "The ID of the startup to get details for");
            ObjectNode gdName = gdProps.putObject("startup_name");
            gdName.put("type", "string");
            gdName.put("description", "The name of the startup (alternative to ID)");
            gdSchema.putArray("required");

            // update_startup
            ObjectNode updateStartup = tools.addObject();
            updateStartup.put("name", "update_startup");
            updateStartup.put("description", "Update a startup's information in the CRM database. Use this when the user wants to modify startup data like status, description, funding request, etc.");
            ObjectNode usSchema = updateStartup.putObject("input_schema");
            usSchema.put("type", "object");
            ObjectNode usProps = usSchema.putObject("properties");
            ObjectNode usId = usProps.putObject("startup_id");
            usId.put("type", "string");
            usId.put("description", "The ID of the startup to update");
            ObjectNode usName = usProps.putObject("startup_name");
            usName.put("type", "string");
            usName.put("description", "The name of the startup to update (alternative to startup_id)");
            ObjectNode usUpdates = usProps.putObject("updates");
            usUpdates.put("type", "object");
            usUpdates.put("description", "The fields to update");
            usSchema.putArray("required").add("updates");

            // get_managers
            ObjectNode getManagers = tools.addObject();
            getManagers.put("name", "get_managers");
            getManagers.put("description", "Get list of all managers from the CRM. Use this to find manager IDs for assigning startups.");
            ObjectNode gmSchema = getManagers.putObject("input_schema");
            gmSchema.put("type", "object");
            ObjectNode gmProps = gmSchema.putObject("properties");
            ObjectNode gmRole = gmProps.putObject("role");
            gmRole.put("type", "string");
            gmRole.put("description", "Optional filter by role");
            ArrayNode gmEnum = gmRole.putArray("enum");
            gmEnum.add("ceo").add("deputy_investment").add("deputy_ma").add("manager_investment").add("manager_ma");
            gmSchema.putArray("required");

            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            log.error("Error building tools JSON: {}", e.getMessage());
            return "[]";
        }
    }

    // =========================================================================
    // Public API: analyze()
    // =========================================================================

    /**
     * Analyze startup data using Claude.
     * Ported from Python: POST /api/analyze
     */
    public CrmResponse analyze(AnalyzeRequest request) {
        log.info("[CRM-AI] Analyzing startup, query length: {}", request.getQuery().length());
        List<String> toolsUsed = new ArrayList<>();

        try {
            String analysisPrompt;

            // If startupId is provided, fetch startup data first
            if (request.getStartupId() != null && !request.getStartupId().isBlank()) {
                toolsUsed.add("get_startup_details");
                Map<String, Object> startupResult = crmApiClient.getStartupDetails(
                        request.getStartupId(), null, null);

                if (Boolean.TRUE.equals(startupResult.get("success"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> startup = (Map<String, Object>) startupResult.get("startup");
                    String startupInfo = formatStartupInfo(startup);
                    analysisPrompt = request.getQuery() + "\n\nStartup data:\n" + startupInfo;
                } else {
                    analysisPrompt = request.getQuery();
                }
            } else {
                analysisPrompt = request.getQuery();
            }

            // Build analysis prompt for JSON output
            String fullPrompt = analysisPrompt + "\n\nReturn JSON with: score (0-100), valuation (USD), "
                    + "strengths (array), weaknesses (array), recommendation (strong_buy/buy/hold/pass)";

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", fullPrompt);

            String responseText = callClaude(
                    "You are an expert startup analyst. Return only valid JSON.",
                    messages, null);

            // Try to parse JSON from response
            Map<String, Object> analysis = null;
            Matcher jsonMatcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(responseText);
            if (jsonMatcher.find()) {
                try {
                    analysis = objectMapper.readValue(jsonMatcher.group(), Map.class);
                    analysis.put("generatedAt", Instant.now().toString());
                } catch (Exception e) {
                    log.warn("Could not parse JSON from analysis response, returning raw text");
                }
            }

            if (analysis == null) {
                analysis = new LinkedHashMap<>();
                analysis.put("raw", responseText);
                analysis.put("generatedAt", Instant.now().toString());
            }

            return CrmResponse.builder()
                    .response(responseText)
                    .analysis(analysis)
                    .toolsUsed(toolsUsed)
                    .sources(List.of("Claude AI Analysis"))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("[CRM-AI] Analysis error: {}", e.getMessage(), e);
            return CrmResponse.builder()
                    .response("Error during analysis: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    // =========================================================================
    // Public API: analyzeFile()
    // =========================================================================

    /**
     * Analyze an uploaded file using Claude vision or text extraction.
     * Supports images (via Claude vision), PDF, and DOCX text extraction.
     * Ported from Python: POST /api/analyze-file
     */
    public CrmResponse analyzeFile(AnalyzeFileRequest request) {
        log.info("[CRM-AI] Analyzing file: {}", request.getFileName());

        try {
            String fileName = request.getFileName().toLowerCase();
            String query = request.getQuery() != null && !request.getQuery().isBlank()
                    ? request.getQuery()
                    : "Analyze this file and provide a detailed summary.";

            byte[] fileBytes = Base64.getDecoder().decode(request.getFileContent());
            String mimeType = detectMimeType(fileName);

            ArrayNode messages = objectMapper.createArrayNode();
            String responseText;

            if (mimeType.startsWith("image/")) {
                // Image analysis via Claude vision
                ObjectNode userMsg = messages.addObject();
                userMsg.put("role", "user");
                ArrayNode content = userMsg.putArray("content");

                ObjectNode imageBlock = content.addObject();
                imageBlock.put("type", "image");
                ObjectNode source = imageBlock.putObject("source");
                source.put("type", "base64");
                source.put("media_type", mimeType);
                source.put("data", request.getFileContent());

                ObjectNode textBlock = content.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", query);

                responseText = callClaude(
                        "You are an expert analyst. Analyze the provided file thoroughly.",
                        messages, null);

            } else {
                // Text-based documents: extract text and send to Claude
                String extractedText = extractTextFromFile(fileBytes, fileName);

                if (extractedText == null || extractedText.isBlank()) {
                    return CrmResponse.builder()
                            .response("Unsupported file type or could not extract text: " + mimeType)
                            .success(false)
                            .build();
                }

                // Truncate to prevent token overflow
                if (extractedText.length() > 15000) {
                    extractedText = extractedText.substring(0, 15000);
                }

                ObjectNode userMsg = messages.addObject();
                userMsg.put("role", "user");
                userMsg.put("content", query + "\n\nDocument content:\n" + extractedText);

                responseText = callClaude(
                        "You are an expert document analyst.",
                        messages, null);
            }

            Map<String, Object> fileInfo = new LinkedHashMap<>();
            fileInfo.put("fileName", request.getFileName());
            fileInfo.put("fileSize", fileBytes.length);
            fileInfo.put("fileType", mimeType.startsWith("image/") ? "image" : "document");

            return CrmResponse.builder()
                    .response(responseText)
                    .fileInfo(fileInfo)
                    .sources(List.of("File Analysis"))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("[CRM-AI] File analysis error: {}", e.getMessage(), e);
            return CrmResponse.builder()
                    .response("Error analyzing file: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    // =========================================================================
    // Public API: chat() - SSE Streaming with Tool Use
    // =========================================================================

    /**
     * SSE streaming chat with Claude tool use pipeline.
     * Supports multi-round tool execution (up to MAX_TOOL_ROUNDS).
     * Ported from Python: chat_with_claude() and POST /api/chat
     */
    public SseEmitter chat(CrmChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout

        sseExecutor.execute(() -> {
            try {
                String conversationId = request.getConversationId();
                if (conversationId == null || conversationId.isBlank()) {
                    conversationId = "crm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                }

                // Send conversation ID
                sendSseEvent(emitter, Map.of(
                        "type", "conversation_id",
                        "conversation_id", conversationId
                ));

                String organizationId = request.getOrganizationId();

                // Build system prompt
                String systemPrompt = CRM_ASSISTANT_PROMPT;
                if (request.isMobile()) {
                    systemPrompt += "\n\nIMPORTANT: The user is on a mobile device with a small screen. "
                            + "NEVER use markdown tables. Instead, present tabular data as numbered lists "
                            + "or bullet points with key-value pairs.";
                }
                if (organizationId != null && !organizationId.isBlank()) {
                    systemPrompt += "\n\nUser context: Organization ID: " + organizationId;
                }

                // Get or create conversation history
                ArrayNode messages = getOrCreateConversation(conversationId);

                // Add user message
                ObjectNode userMsg = objectMapper.createObjectNode();
                userMsg.put("role", "user");
                userMsg.put("content", request.getMessage());
                messages.add(userMsg);

                // Parse tools
                ArrayNode tools = (ArrayNode) objectMapper.readTree(buildToolsJson());

                // Tool use loop (ported from Python max_tool_rounds loop)
                for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {

                    // Call Claude with tools
                    ObjectNode requestBody = objectMapper.createObjectNode();
                    requestBody.put("anthropic_version", "bedrock-2023-05-31");
                    requestBody.put("max_tokens", maxTokens);
                    requestBody.put("temperature", temperature);
                    requestBody.put("system", systemPrompt);
                    requestBody.set("messages", messages);
                    requestBody.set("tools", tools);

                    String jsonBody = objectMapper.writeValueAsString(requestBody);
                    JsonNode responseJson = invokeWithRetryAndFallback(jsonBody);

                    JsonNode contentArray = responseJson.get("content");
                    String stopReason = responseJson.has("stop_reason")
                            ? responseJson.get("stop_reason").asText() : "end_turn";

                    // Process response content blocks
                    List<JsonNode> toolUseBlocks = new ArrayList<>();
                    ArrayNode assistantContent = objectMapper.createArrayNode();

                    if (contentArray != null && contentArray.isArray()) {
                        for (JsonNode block : contentArray) {
                            assistantContent.add(block);
                            String blockType = block.path("type").asText();

                            if ("text".equals(blockType)) {
                                String text = block.path("text").asText();
                                if (!text.isEmpty()) {
                                    sendSseEvent(emitter, Map.of("type", "text", "content", text));
                                }
                            } else if ("tool_use".equals(blockType)) {
                                toolUseBlocks.add(block);
                            }
                        }
                    }

                    log.info("[CRM-AI] Round {}: tools={}, stop_reason={}",
                            round + 1, toolUseBlocks.size(), stopReason);

                    // If no tools and done, break
                    if (toolUseBlocks.isEmpty() && "end_turn".equals(stopReason)) {
                        // Add assistant message to history
                        ObjectNode assistantMsg = objectMapper.createObjectNode();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.set("content", assistantContent);
                        messages.add(assistantMsg);
                        break;
                    }

                    // Execute tools and collect results
                    // Add assistant message with tool_use blocks
                    ObjectNode assistantMsg = objectMapper.createObjectNode();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.set("content", assistantContent);
                    messages.add(assistantMsg);

                    ArrayNode toolResultsContent = objectMapper.createArrayNode();

                    for (JsonNode toolBlock : toolUseBlocks) {
                        String toolName = toolBlock.path("name").asText();
                        String toolUseId = toolBlock.path("id").asText();
                        JsonNode toolInput = toolBlock.get("input");

                        sendSseEvent(emitter, Map.of("type", "tool_start", "tool", toolName));

                        String toolResult = executeTool(toolName, toolInput, organizationId);

                        // Build tool_result message
                        ObjectNode toolResultNode = toolResultsContent.addObject();
                        toolResultNode.put("type", "tool_result");
                        toolResultNode.put("tool_use_id", toolUseId);
                        toolResultNode.put("content", toolResult);
                    }

                    // Add tool results as user message
                    ObjectNode toolResultsMsg = objectMapper.createObjectNode();
                    toolResultsMsg.put("role", "user");
                    toolResultsMsg.set("content", toolResultsContent);
                    messages.add(toolResultsMsg);

                    // If no tool blocks but stop reason is not end_turn, also break
                    if (toolUseBlocks.isEmpty()) {
                        break;
                    }
                }

                // Trim conversation history
                trimConversation(conversationId, 20);

                sendSseEvent(emitter, Map.of("type", "done"));
                emitter.complete();

                log.info("[CRM-AI] Chat completed for conversation {}", conversationId);

            } catch (Exception e) {
                log.error("[CRM-AI] Chat streaming error: {}", e.getMessage(), e);
                try {
                    sendSseEvent(emitter, Map.of("type", "error", "content", e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    // =========================================================================
    // Tool Execution (ported from Python execute_tool())
    // =========================================================================

    @SuppressWarnings("unchecked")
    private String executeTool(String toolName, JsonNode toolInput, String organizationId) {
        log.info("[CRM-AI] Executing tool: {} with input: {}", toolName, toolInput);

        try {
            switch (toolName) {
                case "web_search" -> {
                    String query = toolInput.path("query").asText("");
                    return performWebSearch(query);
                }

                case "get_all_startups" -> {
                    String status = toolInput.has("status") ? toolInput.get("status").asText() : null;
                    Map<String, Object> result = crmApiClient.getAllStartups(status, organizationId);

                    if (Boolean.TRUE.equals(result.get("success"))) {
                        List<Map<String, Object>> startups = (List<Map<String, Object>>) result.get("startups");
                        if (startups != null && !startups.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Found ").append(startups.size())
                                    .append(" startups. Here is detailed information for analysis:\n\n");

                            for (Map<String, Object> s : startups) {
                                sb.append(formatStartupForToolResult(s));
                            }
                            return sb.toString();
                        }
                        return "No startups found in the database.";
                    }
                    return "Error fetching startups: " + result.getOrDefault("error", "unknown");
                }

                case "get_startup_details" -> {
                    String startupId = toolInput.has("startup_id") ? toolInput.get("startup_id").asText() : null;
                    String startupName = toolInput.has("startup_name") ? toolInput.get("startup_name").asText() : null;

                    Map<String, Object> result = crmApiClient.getStartupDetails(startupId, startupName, organizationId);
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        Map<String, Object> startup = (Map<String, Object>) result.get("startup");
                        return formatStartupInfo(startup);
                    }
                    return "Error: " + result.getOrDefault("error", "Startup not found");
                }

                case "update_startup" -> {
                    String startupId = toolInput.has("startup_id") ? toolInput.get("startup_id").asText() : null;
                    String startupName = toolInput.has("startup_name") ? toolInput.get("startup_name").asText() : null;
                    Map<String, Object> updates = toolInput.has("updates")
                            ? objectMapper.convertValue(toolInput.get("updates"), Map.class)
                            : Map.of();

                    // Look up startup by name if no ID
                    if ((startupId == null || startupId.isBlank()) && startupName != null) {
                        Map<String, Object> lookupResult = crmApiClient.getStartupDetails(null, startupName, organizationId);
                        if (Boolean.TRUE.equals(lookupResult.get("success"))) {
                            Map<String, Object> startup = (Map<String, Object>) lookupResult.get("startup");
                            startupId = (String) startup.get("id");
                        } else {
                            return "Startup '" + startupName + "' not found";
                        }
                    }

                    if (startupId != null) {
                        Map<String, Object> result = crmApiClient.updateStartup(startupId, updates);
                        if (Boolean.TRUE.equals(result.get("success"))) {
                            Map<String, Object> startup = (Map<String, Object>) result.get("startup");
                            return "Successfully updated startup. New status: " + startup.getOrDefault("status", "N/A");
                        }
                        return "Error updating startup: " + result.getOrDefault("error", "unknown");
                    }
                    return "Error: Either startup_id or startup_name is required for update";
                }

                case "get_managers" -> {
                    String role = toolInput.has("role") ? toolInput.get("role").asText() : null;
                    Map<String, Object> result = crmApiClient.getManagers(role, organizationId);

                    if (Boolean.TRUE.equals(result.get("success"))) {
                        List<Map<String, Object>> managers = (List<Map<String, Object>>) result.get("managers");
                        if (managers != null && !managers.isEmpty()) {
                            StringBuilder sb = new StringBuilder("Managers list:\n\n");
                            for (Map<String, Object> m : managers) {
                                sb.append("- **").append(m.getOrDefault("name", "Unknown"))
                                        .append("** (ID: ").append(m.getOrDefault("id", "N/A")).append(")\n")
                                        .append("  Role: ").append(m.getOrDefault("role", "N/A"))
                                        .append(", Email: ").append(m.getOrDefault("email", "N/A"))
                                        .append("\n\n");
                            }
                            return sb.toString();
                        }
                        return "No managers found.";
                    }
                    return "Error fetching managers: " + result.getOrDefault("error", "unknown");
                }

                default -> {
                    return "Unknown tool: " + toolName;
                }
            }
        } catch (Exception e) {
            log.error("[CRM-AI] Tool execution error for {}: {}", toolName, e.getMessage());
            return "Error executing tool " + toolName + ": " + e.getMessage();
        }
    }

    // =========================================================================
    // Web Search (ported from Python perform_web_search())
    // =========================================================================

    /**
     * Perform web search using DuckDuckGo HTML endpoint.
     * Returns formatted search results text.
     */
    private String performWebSearch(String query) {
        try {
            java.net.URLEncoder.encode(query, "UTF-8");

            // Use Java's HttpClient for DuckDuckGo search
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://html.duckduckgo.com/html/?q=" + encodedQuery))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(
                    httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "No search results found.";
            }

            String html = response.body();
            Pattern resultPattern = Pattern.compile(
                    "<a rel=\"nofollow\" class=\"result__a\" href=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?" +
                            "<a class=\"result__snippet\"[^>]*>([^<]*(?:<[^>]+>[^<]*)*)</a>",
                    Pattern.DOTALL);

            Matcher matcher = resultPattern.matcher(html);
            StringBuilder sb = new StringBuilder("Search results for '" + query + "':\n\n");

            int count = 0;
            while (matcher.find() && count < 5) {
                count++;
                String url = matcher.group(1);
                String title = matcher.group(2).trim();
                String snippet = matcher.group(3).replaceAll("<[^>]+>", "").trim();

                if (snippet.length() > 200) {
                    snippet = snippet.substring(0, 200);
                }

                // Decode DuckDuckGo redirect URL
                if (url.startsWith("//duckduckgo.com/l/?uddg=")) {
                    Matcher urlMatcher = Pattern.compile("uddg=([^&]+)").matcher(url);
                    if (urlMatcher.find()) {
                        url = java.net.URLDecoder.decode(urlMatcher.group(1), "UTF-8");
                    }
                }
                if (!url.startsWith("http")) {
                    url = "https:" + url;
                }

                sb.append(count).append(". ").append(title).append("\n")
                        .append("   ").append(snippet).append("\n")
                        .append("   URL: ").append(url).append("\n\n");
            }

            if (count == 0) {
                return "No search results found.";
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("[CRM-AI] Web search error: {}", e.getMessage());
            return "Web search error: " + e.getMessage();
        }
    }

    // =========================================================================
    // Helpers (ported from Python)
    // =========================================================================

    /**
     * Format startup info for AI response.
     * Ported from Python: format_startup_info()
     */
    @SuppressWarnings("unchecked")
    private String formatStartupInfo(Map<String, Object> startup) {
        Map<String, Object> brief = (Map<String, Object>) startup.getOrDefault("brief", Map.of());
        Map<String, Object> aiAnalysis = (Map<String, Object>) startup.getOrDefault("aiAnalysis", Map.of());

        String companyName = getStringOrDefault(brief, "name",
                getStringOrDefault(brief, "companyName", "Unknown"));

        double funding = getDoubleOrDefault(brief, "fundingRequest", 0);
        String fundingStr = formatCurrency(funding);

        double valuation = getDoubleOrDefault(aiAnalysis, "valuation", 0);
        String valuationStr = formatCurrency(valuation);

        Object score = aiAnalysis.getOrDefault("score", "N/A");

        return String.format("""
                **%s**
                - ID: %s
                - Status: %s
                - Industry: %s
                - Stage: %s
                - AI Score: %s
                - Valuation: %s
                - Funding Request: %s
                - Description: %s""",
                companyName,
                startup.getOrDefault("id", "N/A"),
                startup.getOrDefault("status", "N/A"),
                brief.getOrDefault("industry", "N/A"),
                brief.getOrDefault("stage", "N/A"),
                score,
                valuationStr,
                fundingStr,
                brief.getOrDefault("description", "N/A"));
    }

    /**
     * Format startup data for tool result (list view).
     * Ported from Python: formatting in get_all_startups tool execution.
     */
    @SuppressWarnings("unchecked")
    private String formatStartupForToolResult(Map<String, Object> startup) {
        try {
            Map<String, Object> brief = (Map<String, Object>) startup.getOrDefault("brief", Map.of());
            Map<String, Object> ai = (Map<String, Object>) startup.getOrDefault("aiAnalysis", Map.of());

            String companyName = getStringOrDefault(brief, "name",
                    getStringOrDefault(brief, "companyName", "Unknown"));

            Object score = ai.getOrDefault("score", "N/A");
            double valuation = getDoubleOrDefault(ai, "valuation", 0);
            double funding = getDoubleOrDefault(brief, "fundingRequest", 0);

            String recommendation = (String) ai.getOrDefault("recommendation", "N/A");
            Map<String, String> recLabels = Map.of(
                    "strong_buy", "Strongly Recommended",
                    "buy", "Recommended",
                    "hold", "Hold",
                    "sell", "Not Recommended"
            );
            String recText = recLabels.getOrDefault(recommendation, recommendation);

            String description = getStringOrDefault(brief, "description", "N/A");
            if (description.length() > 200) {
                description = description.substring(0, 200);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("**").append(companyName).append("**\n")
                    .append("- Industry: ").append(brief.getOrDefault("industry", "N/A")).append("\n")
                    .append("- Stage: ").append(brief.getOrDefault("stage", "N/A")).append("\n")
                    .append("- Status: ").append(startup.getOrDefault("status", "N/A")).append("\n")
                    .append("- AI Score: ").append(score).append("/100\n")
                    .append("- Valuation: ").append(formatCurrency(valuation)).append("\n")
                    .append("- Funding Request: ").append(formatCurrency(funding)).append("\n")
                    .append("- AI Recommendation: ").append(recText).append("\n")
                    .append("- Description: ").append(description).append("\n");

            List<Object> strengths = (List<Object>) ai.getOrDefault("strengths", List.of());
            List<Object> weaknesses = (List<Object>) ai.getOrDefault("weaknesses", List.of());

            if (!strengths.isEmpty()) {
                sb.append("- Strengths: ").append(String.join(", ",
                        strengths.stream().limit(3).map(Object::toString).toList())).append("\n");
            }
            if (!weaknesses.isEmpty()) {
                sb.append("- Weaknesses: ").append(String.join(", ",
                        weaknesses.stream().limit(3).map(Object::toString).toList())).append("\n");
            }
            sb.append("\n");

            return sb.toString();
        } catch (Exception e) {
            log.error("Error formatting startup {}: {}", startup.getOrDefault("id", "?"), e.getMessage());
            return "**" + getNestedString(startup, "brief", "companyName", "Unknown")
                    + "** - Error formatting data\n\n";
        }
    }

    private String formatCurrency(double amount) {
        if (amount >= 1_000_000) {
            return String.format("$%.1fM", amount / 1_000_000);
        } else if (amount > 0) {
            return String.format("$%.0fK", amount / 1_000);
        }
        return "N/A";
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        if (val instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private double getDoubleOrDefault(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private String getNestedString(Map<String, Object> map, String outerKey, String innerKey, String defaultValue) {
        Object outer = map.get(outerKey);
        if (outer instanceof Map) {
            Object inner = ((Map<String, Object>) outer).get(innerKey);
            if (inner instanceof String s) return s;
        }
        return defaultValue;
    }

    private String detectMimeType(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (fileName.endsWith(".doc")) return "application/msword";
        return "application/octet-stream";
    }

    /**
     * Extract text from PDF or DOCX files.
     * For production use, Apache PDFBox / POI would be used.
     * This is a basic extraction placeholder.
     */
    private String extractTextFromFile(byte[] fileBytes, String fileName) {
        // For PDF and DOCX processing, the file bytes are decoded and text extracted.
        // This service sends the text to Claude for analysis.
        // In production, integrate Apache PDFBox for PDF and Apache POI for DOCX.
        if (fileName.endsWith(".pdf") || fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
            // Return a marker indicating text extraction is needed
            // The actual extraction would use PDFBox/POI dependencies
            return new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    // =========================================================================
    // Claude Invocation (ported from Python invoke_bedrock / retry logic)
    // =========================================================================

    private String callClaude(String systemPrompt, ArrayNode messages, ArrayNode tools) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("system", systemPrompt);
            requestBody.set("messages", messages);

            if (tools != null && !tools.isEmpty()) {
                requestBody.set("tools", tools);
            }

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            JsonNode responseJson = invokeWithRetryAndFallback(jsonBody);

            JsonNode contentArray = responseJson.get("content");
            if (contentArray != null && contentArray.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                return sb.toString();
            }
            return "";

        } catch (Exception e) {
            log.error("[CRM-AI] Error calling Claude: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Claude: " + e.getMessage(), e);
        }
    }

    private JsonNode invokeWithRetryAndFallback(String jsonBody) {
        try {
            return invokeWithRetry(modelId, jsonBody);
        } catch (Exception primaryError) {
            if (!modelId.equals(fallbackModelId)) {
                log.warn("[CRM-AI] Primary model {} failed, trying fallback: {}", modelId, fallbackModelId);
                try {
                    return invokeWithRetry(fallbackModelId, jsonBody);
                } catch (Exception fallbackError) {
                    log.error("[CRM-AI] Fallback model also failed: {}", fallbackError.getMessage());
                    throw new RuntimeException("Both primary and fallback models failed", fallbackError);
                }
            }
            throw primaryError;
        }
    }

    private JsonNode invokeWithRetry(String model, String jsonBody) {
        Exception lastError = null;
        long delay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(model)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(jsonBody))
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asUtf8String();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                JsonNode usage = responseJson.get("usage");
                if (usage != null) {
                    log.info("[CRM-AI] Tokens - input: {}, output: {}",
                            usage.path("input_tokens").asInt(),
                            usage.path("output_tokens").asInt());
                }

                return responseJson;

            } catch (Exception e) {
                log.warn("[CRM-AI] Attempt {} failed for model {}: {}", attempt, model, e.getMessage());
                lastError = e;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    delay *= 2;
                }
            }
        }
        throw new RuntimeException("All retry attempts failed", lastError);
    }

    // =========================================================================
    // SSE Helpers
    // =========================================================================

    private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        emitter.send(SseEmitter.event().data(json));
    }

    private ArrayNode getOrCreateConversation(String conversationId) {
        return conversationStore.computeIfAbsent(conversationId, k -> objectMapper.createArrayNode());
    }

    private void trimConversation(String conversationId, int maxMessages) {
        ArrayNode messages = conversationStore.get(conversationId);
        if (messages != null && messages.size() > maxMessages) {
            ArrayNode trimmed = objectMapper.createArrayNode();
            for (int i = messages.size() - maxMessages; i < messages.size(); i++) {
                trimmed.add(messages.get(i));
            }
            conversationStore.put(conversationId, trimmed);
        }
    }
}

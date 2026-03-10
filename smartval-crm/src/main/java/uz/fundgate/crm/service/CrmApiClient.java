package uz.fundgate.crm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * WebClient-based HTTP client for external CRM API calls.
 * Provides methods to fetch and update startup data from the CRM backend.
 *
 * Ported from Python: get_startups_from_crm(), get_startup_details_from_crm(),
 *                      update_startup_in_crm(), get_managers() in main.py
 */
@Slf4j
@Service
public class CrmApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CrmApiClient(
            @Value("${crm.api.url:https://api-kpsspj764a-uc.a.run.app}") String crmApiUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(crmApiUrl)
                .build();
        this.objectMapper = objectMapper;
        log.info("CRM API Client initialized with base URL: {}", crmApiUrl);
    }

    /**
     * Fetch all startups from CRM API.
     * Ported from Python: get_startups_from_crm()
     *
     * @param status         optional status filter (new, in_review, pipeline, portfolio, rejected)
     * @param organizationId required organization ID for data scoping
     * @return map with "success" flag and "startups" list
     */
    public Map<String, Object> getAllStartups(String status, String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Map.of(
                    "success", false,
                    "error", "organizationId is required for security - please ensure you're logged in"
            );
        }

        try {
            WebClient.RequestHeadersSpec<?> request = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/crm/startups")
                                .queryParam("organizationId", organizationId);
                        if (status != null && !status.isBlank()) {
                            uriBuilder.queryParam("status", status);
                        }
                        return uriBuilder.build();
                    });

            String responseBody = request.retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode data = objectMapper.readTree(responseBody);
            JsonNode startupsNode = data.has("startups") ? data.get("startups")
                    : data.has("data") ? data.get("data")
                    : objectMapper.createArrayNode();

            List<Map<String, Object>> startups = new ArrayList<>();
            if (startupsNode.isArray()) {
                for (JsonNode node : startupsNode) {
                    startups.add(objectMapper.convertValue(node, Map.class));
                }
            }

            return Map.of("success", true, "startups", startups);

        } catch (Exception e) {
            log.error("Error fetching startups: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Fetch detailed startup info by ID or name.
     * Ported from Python: get_startup_details_from_crm()
     *
     * @param startupId      startup ID (preferred)
     * @param startupName    startup name (fallback, searches by name match)
     * @param organizationId required organization ID
     * @return map with "success" flag and "startup" data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStartupDetails(String startupId, String startupName, String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Map.of(
                    "success", false,
                    "error", "organizationId is required for security - please ensure you're logged in"
            );
        }

        try {
            if (startupId != null && !startupId.isBlank()) {
                // Direct lookup by ID
                String responseBody = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/crm/startups/{id}")
                                .queryParam("organizationId", organizationId)
                                .build(startupId))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                JsonNode data = objectMapper.readTree(responseBody);
                JsonNode startupNode = data.has("startup") ? data.get("startup")
                        : data.has("data") ? data.get("data")
                        : data;

                return Map.of("success", true, "startup", objectMapper.convertValue(startupNode, Map.class));

            } else if (startupName != null && !startupName.isBlank()) {
                // Search by name: fetch all and filter
                Map<String, Object> allResult = getAllStartups(null, organizationId);
                if (Boolean.TRUE.equals(allResult.get("success"))) {
                    List<Map<String, Object>> startups = (List<Map<String, Object>>) allResult.get("startups");

                    for (Map<String, Object> startup : startups) {
                        Map<String, Object> brief = (Map<String, Object>) startup.getOrDefault("brief", Map.of());
                        String companyName = (String) brief.getOrDefault("companyName", "");
                        if (companyName.isEmpty()) {
                            companyName = (String) brief.getOrDefault("name", "");
                        }

                        if (companyName.equalsIgnoreCase(startupName)
                                || companyName.toLowerCase().contains(startupName.toLowerCase())) {
                            return Map.of("success", true, "startup", startup);
                        }
                    }
                    return Map.of("success", false, "error", "Startup '" + startupName + "' not found");
                }
                return allResult;
            }

            return Map.of("success", false, "error", "Either startup_id or startup_name is required");

        } catch (Exception e) {
            log.error("Error fetching startup details: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Update startup data in CRM via PATCH API call.
     * Supports nested field updates using dot notation for Firestore.
     * Ported from Python: update_startup_in_crm()
     *
     * @param startupId startup ID
     * @param updates   map of fields to update (may be nested)
     * @return map with "success" flag and updated "startup" data
     */
    public Map<String, Object> updateStartup(String startupId, Map<String, Object> updates) {
        try {
            Map<String, Object> flattenedUpdates = flattenUpdates(updates, "");
            log.info("Updating startup {} with: {}", startupId, flattenedUpdates);

            String responseBody = webClient.patch()
                    .uri("/crm/startups/{id}", startupId)
                    .bodyValue(flattenedUpdates)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode data = objectMapper.readTree(responseBody);
            JsonNode startupNode = data.has("startup") ? data.get("startup")
                    : data.has("data") ? data.get("data")
                    : data;

            return Map.of("success", true, "startup", objectMapper.convertValue(startupNode, Map.class));

        } catch (Exception e) {
            log.error("Error updating startup: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Fetch list of managers from CRM.
     * Ported from Python: get_managers tool execution in execute_tool()
     *
     * @param role           optional role filter
     * @param organizationId organization ID
     * @return map with managers list
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getManagers(String role, String organizationId) {
        try {
            String responseBody = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/crm/managers");
                        if (organizationId != null && !organizationId.isBlank()) {
                            uriBuilder.queryParam("organizationId", organizationId);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode data = objectMapper.readTree(responseBody);
            JsonNode managersNode = data.has("data") ? data.get("data")
                    : data.has("managers") ? data.get("managers")
                    : objectMapper.createArrayNode();

            List<Map<String, Object>> managers = new ArrayList<>();
            if (managersNode.isArray()) {
                for (JsonNode node : managersNode) {
                    Map<String, Object> manager = objectMapper.convertValue(node, Map.class);
                    if (role == null || role.isBlank()
                            || role.equals(manager.getOrDefault("role", ""))) {
                        managers.add(manager);
                    }
                }
            }

            return Map.of("success", true, "managers", managers);

        } catch (Exception e) {
            log.error("Error fetching managers: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Flatten nested dict using dot notation for Firestore-style updates.
     * Ported from Python: flatten_updates()
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenUpdates(Map<String, Object> updates, String parentKey) {
        Map<String, Object> items = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String newKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();

            if (entry.getValue() instanceof Map) {
                Map<String, Object> nested = (Map<String, Object>) entry.getValue();
                if (!nested.isEmpty()) {
                    items.putAll(flattenUpdates(nested, newKey));
                } else {
                    items.put(newKey, entry.getValue());
                }
            } else {
                items.put(newKey, entry.getValue());
            }
        }

        return items;
    }
}

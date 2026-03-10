package uz.fundgate.fundgate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for validating URLs using Spring WebClient (reactive HTTP).
 *
 * Validates:
 * - Website accessibility
 * - LinkedIn profile URLs
 * - Video links (YouTube, Google Drive)
 * - General URL format and reachability
 */
@Slf4j
@Service
public class LinkValidatorService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final Pattern LINKEDIN_PERSONAL = Pattern.compile(
            "^https?://(?:www\\.)?linkedin\\.com/in/[\\w-]+/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN_COMPANY = Pattern.compile(
            "^https?://(?:www\\.)?linkedin\\.com/company/[\\w-]+/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "^https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)[\\w-]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_DRIVE_PATTERN = Pattern.compile(
            "^https?://(?:drive\\.google\\.com|docs\\.google\\.com)/", Pattern.CASE_INSENSITIVE);

    private final WebClient webClient;

    public LinkValidatorService() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * Check if a URL is accessible (returns HTTP 200).
     */
    public boolean isAccessible(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            HttpStatusCode status = webClient.get()
                    .uri(url.trim())
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode())
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorReturn(HttpStatusCode.valueOf(500))
                    .block();

            return status != null && status.is2xxSuccessful();
        } catch (Exception e) {
            log.debug("URL not accessible: {} - {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Detect link type.
     */
    public String detectLinkType(String url) {
        if (url == null || url.isBlank()) return "unknown";
        url = url.trim();

        if (LINKEDIN_PERSONAL.matcher(url).matches()) return "linkedin_personal";
        if (LINKEDIN_COMPANY.matcher(url).matches()) return "linkedin_company";
        if (YOUTUBE_PATTERN.matcher(url).matches()) return "youtube";
        if (GOOGLE_DRIVE_PATTERN.matcher(url).matches()) return "google_drive";
        if (url.startsWith("http://") || url.startsWith("https://")) return "website";

        return "unknown";
    }

    /**
     * Check if URL is a valid LinkedIn profile.
     */
    public boolean isValidLinkedIn(String url) {
        if (url == null) return false;
        return LINKEDIN_PERSONAL.matcher(url.trim()).matches()
                || LINKEDIN_COMPANY.matcher(url.trim()).matches();
    }

    /**
     * Check if URL is a valid video link (YouTube or Google Drive).
     */
    public boolean isValidVideoLink(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        return YOUTUBE_PATTERN.matcher(trimmed).matches()
                || GOOGLE_DRIVE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Validate a URL and return a results map with type, validity, and accessibility.
     */
    public Map<String, Object> validateUrl(String url) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        result.put("url", url != null ? url : "");
        result.put("linkType", detectLinkType(url));

        boolean validFormat = isValidUrlFormat(url);
        result.put("isValid", validFormat);

        if (validFormat) {
            boolean accessible = isAccessible(url);
            result.put("isAccessible", accessible);
        } else {
            result.put("isAccessible", false);
        }

        return result;
    }

    /**
     * Check if a URL has valid format.
     */
    public boolean isValidUrlFormat(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return uri.getScheme() != null
                    && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                    && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}

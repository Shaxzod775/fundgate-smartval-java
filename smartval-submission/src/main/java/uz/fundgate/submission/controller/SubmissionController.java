package uz.fundgate.submission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uz.fundgate.common.dto.ApiResponse;
import uz.fundgate.common.dto.PageResponse;
import uz.fundgate.common.security.UserContext;
import uz.fundgate.common.security.UserContextHolder;
import uz.fundgate.submission.dto.SubmissionFormRequest;
import uz.fundgate.submission.dto.SubmissionResponse;
import uz.fundgate.submission.service.SubmissionService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Submissions", description = "Startup submission intake API")
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/submit")
    @Operation(summary = "Submit a new startup application")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submit(
            @Valid @RequestBody SubmissionFormRequest request
    ) {
        log.info("Received submission for startup: {}", request.getStartupName());

        UserContext userContext = UserContextHolder.getCurrentUser();
        SubmissionResponse response = submissionService.submit(request, userContext);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/api/submissions")
    @Operation(summary = "List all submissions (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<SubmissionResponse>>> listAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<SubmissionResponse> page = submissionService.listAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/api/submissions/{id}")
    @Operation(summary = "Get submission by ID")
    public ResponseEntity<ApiResponse<SubmissionResponse>> getById(@PathVariable UUID id) {
        SubmissionResponse response = submissionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/submissions/{id}/status")
    @Operation(summary = "Check submission status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable UUID id) {
        SubmissionResponse response = submissionService.getById(id);

        Map<String, Object> statusInfo = Map.of(
                "id", response.getId(),
                "status", response.getStatus(),
                "score", response.getScore() != null ? response.getScore() : "N/A",
                "verdict", response.getVerdict() != null ? response.getVerdict() : "N/A",
                "analysisId", response.getAnalysisId() != null ? response.getAnalysisId() : "N/A"
        );

        return ResponseEntity.ok(ApiResponse.success(statusInfo));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "smartval-submission"));
    }
}

package uz.fundgate.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.fundgate.common.dto.PageResponse;
import uz.fundgate.common.event.AnalysisRequestedEvent;
import uz.fundgate.common.event.EventPublisher;
import uz.fundgate.common.exception.NotFoundException;
import uz.fundgate.common.security.UserContext;
import uz.fundgate.submission.dto.SubmissionFormRequest;
import uz.fundgate.submission.dto.SubmissionResponse;
import uz.fundgate.submission.entity.Submission;
import uz.fundgate.submission.entity.SubmissionStatus;
import uz.fundgate.submission.repository.SubmissionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubmissionResponse submit(SubmissionFormRequest request, UserContext userContext) {
        log.info("Processing submission for startup: {}", request.getStartupName());

        String rawFormData = serializeToJson(request);

        Submission submission = Submission.builder()
                .startupName(request.getStartupName())
                .founderEmail(request.getFounderEmail())
                .founderName(userContext != null ? userContext.getName() : null)
                .description(request.getDescription())
                .stage(request.getStage())
                .sector(request.getSector())
                .teamSize(request.getTeamSize())
                .pitchDeckUrl(request.getPitchDeckUrl())
                .businessPlanUrl(request.getBusinessPlanUrl())
                .status(SubmissionStatus.PENDING)
                .rawFormData(rawFormData)
                .build();

        submission = submissionRepository.save(submission);
        log.info("Submission saved with id: {}", submission.getId());

        publishAnalysisEvent(submission, userContext);

        return toResponse(submission, "Application submitted successfully. Analysis is in progress.");
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission", id));
        return toResponse(submission, null);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getByEmail(String email) {
        return submissionRepository.findByFounderEmail(email).stream()
                .map(s -> toResponse(s, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> listAll(Pageable pageable) {
        Page<SubmissionResponse> page = submissionRepository.findAll(pageable)
                .map(s -> toResponse(s, null));
        return PageResponse.from(page);
    }

    @Transactional
    public void updateStatus(UUID id, SubmissionStatus status, Double score, String verdict, String analysisId) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission", id));

        submission.setStatus(status);
        if (score != null) {
            submission.setFundgateScore(score);
        }
        if (verdict != null) {
            submission.setVerdict(verdict);
        }
        if (analysisId != null) {
            submission.setAnalysisId(analysisId);
        }

        submissionRepository.save(submission);
        log.info("Submission {} status updated to {} (score={}, verdict={})", id, status, score, verdict);
    }

    private void publishAnalysisEvent(Submission submission, UserContext userContext) {
        try {
            AnalysisRequestedEvent event = AnalysisRequestedEvent.builder()
                    .submissionId(submission.getId().toString())
                    .userId(userContext != null ? userContext.getUid() : null)
                    .email(submission.getFounderEmail())
                    .build();

            eventPublisher.publish("analysis.requested", event);
            log.info("Analysis requested event published for submission: {}", submission.getId());
        } catch (Exception e) {
            log.error("Failed to publish analysis event for submission: {}. Error: {}",
                    submission.getId(), e.getMessage());
        }
    }

    private SubmissionResponse toResponse(Submission submission, String message) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .startupName(submission.getStartupName())
                .status(submission.getStatus().name())
                .message(message)
                .analysisId(submission.getAnalysisId())
                .score(submission.getFundgateScore())
                .verdict(submission.getVerdict())
                .createdAt(submission.getCreatedAt())
                .build();
    }

    private String serializeToJson(SubmissionFormRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize form data to JSON: {}", e.getMessage());
            return null;
        }
    }
}

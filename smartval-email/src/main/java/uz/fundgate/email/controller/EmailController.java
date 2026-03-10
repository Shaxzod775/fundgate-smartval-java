package uz.fundgate.email.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.fundgate.email.dto.EmailRequest;
import uz.fundgate.email.dto.EmailResponse;
import uz.fundgate.email.dto.FundgateEmailRequest;
import uz.fundgate.email.dto.SmartvalEmailRequest;
import uz.fundgate.email.service.EmailService;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for email operations.
 * Provides endpoints for sending FundGate results, SmartVal results,
 * generic, and test emails.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email", description = "Email sending endpoints for FundGate & SmartVal platforms")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send/fundgate")
    @Operation(summary = "Send FundGate analysis results email",
            description = "Sends a professional email with FundGate startup evaluation results including score, verdict, category breakdown, and blockers")
    public ResponseEntity<EmailResponse> sendFundgateEmail(@Valid @RequestBody FundgateEmailRequest request) {
        log.info("POST /send/fundgate - to: {}, startup: {}", request.getTo(), request.getStartupName());

        try {
            emailService.sendFundgateResultsEmail(request);
            return ResponseEntity.ok(EmailResponse.ok("FundGate results email queued for " + request.getTo()));
        } catch (Exception e) {
            log.error("Failed to send FundGate email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(EmailResponse.error("Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/send/smartval")
    @Operation(summary = "Send SmartVal valuation results email",
            description = "Sends a professional email with SmartVal startup valuation results including method and estimated value")
    public ResponseEntity<EmailResponse> sendSmartvalEmail(@Valid @RequestBody SmartvalEmailRequest request) {
        log.info("POST /send/smartval - to: {}, startup: {}", request.getTo(), request.getStartupName());

        try {
            emailService.sendSmartvalResultsEmail(request);
            return ResponseEntity.ok(EmailResponse.ok("SmartVal results email queued for " + request.getTo()));
        } catch (Exception e) {
            log.error("Failed to send SmartVal email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(EmailResponse.error("Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/send/generic")
    @Operation(summary = "Send a generic email",
            description = "Sends a generic email with optional Thymeleaf template support")
    public ResponseEntity<EmailResponse> sendGenericEmail(@Valid @RequestBody EmailRequest request) {
        log.info("POST /send/generic - to: {}, subject: {}", request.getTo(), request.getSubject());

        try {
            emailService.sendGenericEmail(request);
            return ResponseEntity.ok(EmailResponse.ok("Generic email queued for " + request.getTo()));
        } catch (Exception e) {
            log.error("Failed to send generic email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(EmailResponse.error("Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/send/test")
    @Operation(summary = "Send a test email",
            description = "Sends a test email to verify SMTP configuration is working correctly")
    public ResponseEntity<EmailResponse> sendTestEmail(
            @RequestParam @NotBlank @Email String to) {
        log.info("POST /send/test - to: {}", to);

        try {
            emailService.sendTestEmail(to);
            return ResponseEntity.ok(EmailResponse.ok("Test email sent to " + to));
        } catch (Exception e) {
            log.error("Failed to send test email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(EmailResponse.error("Failed to send test email: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "smartval-email",
                "timestamp", Instant.now().toString()
        ));
    }
}

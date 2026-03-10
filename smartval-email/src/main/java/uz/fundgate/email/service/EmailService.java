package uz.fundgate.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import uz.fundgate.email.dto.EmailRequest;
import uz.fundgate.email.dto.FundgateEmailRequest;
import uz.fundgate.email.dto.SmartvalEmailRequest;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service responsible for composing and sending emails via JavaMailSender.
 * Uses Thymeleaf for HTML email template rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${fundgate.email.from:noreply@fundgate.uz}")
    private String fromAddress;

    @Value("${fundgate.email.reply-to:support@fundgate.uz}")
    private String replyToAddress;

    /**
     * Sends a generic email. If a templateName is provided, renders the Thymeleaf template;
     * otherwise, wraps the body in the generic template.
     */
    @Async
    public void sendGenericEmail(EmailRequest request) {
        log.info("Sending generic email to: {}", request.getTo());

        Map<String, Object> variables = request.getVariables() != null
                ? new HashMap<>(request.getVariables())
                : new HashMap<>();
        variables.put("subject", request.getSubject());
        variables.put("body", request.getBody());
        variables.put("year", LocalDateTime.now().getYear());

        String templateName = request.getTemplateName() != null
                ? "email/" + request.getTemplateName()
                : "email/generic";

        String html = renderTemplate(templateName, variables);
        sendHtmlEmail(request.getTo(), request.getSubject(), html);
    }

    /**
     * Sends FundGate analysis results email with score, verdict, categories, and blockers.
     */
    @Async
    public void sendFundgateResultsEmail(FundgateEmailRequest request) {
        log.info("Sending FundGate results email to: {} for startup: {}", request.getTo(), request.getStartupName());

        String verdictLabel = resolveVerdictLabel(request.getVerdict());
        String verdictColor = resolveVerdictColor(request.getVerdict());

        Map<String, Object> variables = new HashMap<>();
        variables.put("startupName", request.getStartupName());
        variables.put("score", request.getScore());
        variables.put("verdict", request.getVerdict());
        variables.put("verdictLabel", verdictLabel);
        variables.put("verdictColor", verdictColor);
        variables.put("categories", request.getCategories());
        variables.put("blockers", request.getBlockers());
        variables.put("summary", request.getSummary());
        variables.put("analysisUrl", request.getAnalysisUrl());
        variables.put("year", LocalDateTime.now().getYear());
        variables.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        String html = renderTemplate("email/fundgate-results", variables);
        String subject = "FundGate Evaluation Results: " + request.getStartupName();
        sendHtmlEmail(request.getTo(), subject, html);
    }

    /**
     * Sends SmartVal valuation results email.
     */
    @Async
    public void sendSmartvalResultsEmail(SmartvalEmailRequest request) {
        log.info("Sending SmartVal results email to: {} for startup: {}", request.getTo(), request.getStartupName());

        String formattedValuation = NumberFormat.getCurrencyInstance(Locale.US).format(request.getValuation());
        String methodLabel = resolveMethodLabel(request.getMethod());

        Map<String, Object> variables = new HashMap<>();
        variables.put("startupName", request.getStartupName());
        variables.put("method", request.getMethod());
        variables.put("methodLabel", methodLabel);
        variables.put("valuation", request.getValuation());
        variables.put("formattedValuation", formattedValuation);
        variables.put("details", request.getDetails());
        variables.put("year", LocalDateTime.now().getYear());
        variables.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        String html = renderTemplate("email/smartval-results", variables);
        String subject = "SmartVal: Valuation Complete - " + request.getStartupName();
        sendHtmlEmail(request.getTo(), subject, html);
    }

    /**
     * Sends a test email to verify SMTP configuration.
     */
    public void sendTestEmail(String to) {
        log.info("Sending test email to: {}", to);

        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", "Email Service Test");
        variables.put("body", "This is a test email from the FundGate/SmartVal Email Service. "
                + "If you received this, the email configuration is working correctly!");
        variables.put("year", LocalDateTime.now().getYear());

        String html = renderTemplate("email/generic", variables);
        sendHtmlEmail(to, "FundGate Email Service - Test", html);
    }

    /**
     * Renders a Thymeleaf template with the given variables.
     */
    private String renderTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    /**
     * Sends an HTML email via JavaMailSender.
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setReplyTo(replyToAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

    private String resolveVerdictLabel(String verdict) {
        return switch (verdict.toLowerCase()) {
            case "blocked" -> "Requires Fixes";
            case "needs_improvement" -> "Needs Improvement";
            case "ready_to_route" -> "Ready for Routing";
            default -> verdict;
        };
    }

    private String resolveVerdictColor(String verdict) {
        return switch (verdict.toLowerCase()) {
            case "blocked" -> "#dc3545";
            case "needs_improvement" -> "#f59e0b";
            case "ready_to_route" -> "#10b981";
            default -> "#6b7280";
        };
    }

    private String resolveMethodLabel(String method) {
        return switch (method.toLowerCase()) {
            case "berkus" -> "Berkus Method";
            case "scorecard" -> "Scorecard Method";
            case "riskfactor" -> "Risk Factor Summation";
            case "dcf" -> "Discounted Cash Flow";
            case "comparables" -> "Comparable Transactions";
            default -> method;
        };
    }
}

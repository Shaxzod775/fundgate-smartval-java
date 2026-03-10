package uz.fundgate.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing statistics report data.
 * Aggregates platform metrics for daily/weekly reports.
 *
 * Ported from Python: data dict structure returned by fetch_statistics()
 * and consumed by format_daily_report() / format_weekly_report() in tasks.py
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportData {

    /**
     * Report date or period string (e.g., "10.03.2026" or "04.03 - 10.03.2026").
     */
    private String date;

    // ── Users ─────────────────────────────────────────────
    private int newUsers;
    private int totalUsers;

    // ── Startups ──────────────────────────────────────────
    private int startupsCreated;
    private int totalStartups;

    // ── Analyses ──────────────────────────────────────────
    private int fundgateAnalyses;
    private int smartvalTotal;
    private int berkusAnalyses;
    private int scorecardAnalyses;
    private int riskFactorAnalyses;

    // ── ChatKit ───────────────────────────────────────────
    private int chatkitMessages;

    // ── Feedback ──────────────────────────────────────────
    private int feedbackCount;

    // ── Computed fields ───────────────────────────────────

    /**
     * Total number of all analyses (FundGate + SmartVal).
     */
    public int getTotalAnalyses() {
        return fundgateAnalyses + smartvalTotal;
    }
}

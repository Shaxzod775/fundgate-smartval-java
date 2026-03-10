package uz.fundgate.fundgate.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.fundgate.fundgate.dto.FundGateResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for saving FundGate analysis results to Google Cloud Firestore.
 *
 * Handles:
 * - Saving evaluation results to the 'startups' collection
 * - Retrieving user email for notifications
 * - Incrementing daily statistics counters
 */
@Slf4j
@Service
public class FirebaseStorageService {

    private final Firestore firestore;

    public FirebaseStorageService(@Nullable Firestore firestore) {
        this.firestore = firestore;
        if (firestore == null) {
            log.warn("Firestore client is null - Firebase features will be unavailable");
        } else {
            log.info("FirebaseStorageService initialized");
        }
    }

    /**
     * Save FundGate evaluation results to Firestore.
     *
     * @param startupId  startup document ID
     * @param ownerId    owner user UID
     * @param response   FundGate API response data
     * @return true if saved successfully
     */
    public boolean saveAnalysisResult(String startupId, String ownerId, FundGateResponse response) {
        if (firestore == null) {
            log.warn("Firestore not available, cannot save analysis");
            return false;
        }

        try {
            DocumentReference startupRef = firestore.collection("startups").document(startupId);

            // Prepare FundGate evaluation data
            Map<String, Object> fundgateData = new HashMap<>();
            fundgateData.put("scores", response.getScores());
            fundgateData.put("total", response.getTotal());
            fundgateData.put("status", response.getStatus());
            fundgateData.put("blockers", response.getBlockers());
            fundgateData.put("recommendations", response.getRecommendations());
            fundgateData.put("evaluatedAt", FieldValue.serverTimestamp());
            fundgateData.put("version", response.getVersion());

            if (response.getStartupComment() != null) {
                Map<String, Object> commentMap = new HashMap<>();
                commentMap.put("strengths", response.getStartupComment().getStrengths());
                commentMap.put("weaknesses", response.getStartupComment().getWeaknesses());
                commentMap.put("overall_comment", response.getStartupComment().getOverallComment());
                commentMap.put("detailed_comment", response.getStartupComment().getDetailedComment());
                commentMap.put("recommendation", response.getStartupComment().getRecommendation());
                fundgateData.put("startup_comment", commentMap);
            }

            // Update document
            Map<String, Object> updates = new HashMap<>();
            updates.put("fundGateEvaluation", fundgateData);
            updates.put("aiScore", response.getTotal());
            updates.put("lastUpdated", FieldValue.serverTimestamp());

            DocumentSnapshot doc = startupRef.get().get();
            if (doc.exists()) {
                startupRef.update(updates).get();
            } else {
                startupRef.set(updates).get();
            }

            log.info("FundGate results saved to Firebase for startup_id: {}", startupId);

            // Increment daily statistics
            incrementDailyStatistics("fundgateAnalyses");

            return true;

        } catch (Exception e) {
            log.error("Error saving FundGate results to Firebase: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get user email from Firebase by ownerId.
     *
     * @param ownerId   user UID
     * @param startupId startup ID (optional, to check in startups collection)
     * @return user email or null if not found
     */
    public String getUserEmail(String ownerId, String startupId) {
        if (firestore == null) {
            log.warn("Firestore not available, cannot get email");
            return null;
        }

        try {
            // Try users collection first
            DocumentSnapshot userDoc = firestore.collection("users").document(ownerId).get().get();
            if (userDoc.exists()) {
                String email = userDoc.getString("email");
                if (email != null && !email.isBlank()) {
                    log.info("Email found in users: {}", email);
                    return email;
                }
            }

            // Try startups collection
            if (startupId != null && !startupId.isBlank()) {
                DocumentSnapshot startupDoc = firestore.collection("startups").document(startupId).get().get();
                if (startupDoc.exists()) {
                    String email = startupDoc.getString("email");
                    if (email == null) email = startupDoc.getString("ownerEmail");
                    if (email == null) email = startupDoc.getString("userEmail");
                    if (email != null && !email.isBlank()) {
                        log.info("Email found in startups: {}", email);
                        return email;
                    }
                }
            }

            log.warn("Email not found for owner_id: {}", ownerId);
            return null;

        } catch (Exception e) {
            log.error("Error getting email from Firebase: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Retrieve analysis result from Firestore by startup ID.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnalysisResult(String startupId) {
        if (firestore == null) {
            log.warn("Firestore not available");
            return null;
        }

        try {
            DocumentSnapshot doc = firestore.collection("startups").document(startupId).get().get();
            if (doc.exists() && doc.contains("fundGateEvaluation")) {
                return (Map<String, Object>) doc.get("fundGateEvaluation");
            }
            return null;
        } catch (Exception e) {
            log.error("Error retrieving analysis from Firebase: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Increment daily statistics counter.
     */
    private void incrementDailyStatistics(String statType) {
        if (firestore == null) return;

        try {
            String dateStr = Instant.now().toString().substring(0, 10); // YYYY-MM-DD
            DocumentReference statsRef = firestore.collection("daily_statistics").document(dateStr);

            Map<String, Object> data = new HashMap<>();
            data.put("date", dateStr);
            data.put(statType, FieldValue.increment(1));
            data.put("lastUpdated", FieldValue.serverTimestamp());

            statsRef.set(data, SetOptions.merge()).get();
            log.info("Incremented daily statistics: {} for {}", statType, dateStr);

        } catch (Exception e) {
            log.error("Error incrementing daily statistics: {}", e.getMessage());
        }
    }
}

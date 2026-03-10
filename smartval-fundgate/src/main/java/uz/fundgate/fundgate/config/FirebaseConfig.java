package uz.fundgate.fundgate.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Firebase Admin SDK initialization and Firestore client configuration.
 * Supports both credential-file based and Application Default Credentials (ADC) authentication.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${fundgate.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${FIREBASE_PROJECT_ID:fundgate-smartval}")
    private String projectId;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setProjectId(projectId);

                if (credentialsPath != null && !credentialsPath.isBlank()) {
                    log.info("Initializing Firebase with credentials file: {}", credentialsPath);
                    GoogleCredentials credentials = GoogleCredentials.fromStream(
                            new FileInputStream(credentialsPath));
                    builder.setCredentials(credentials);
                } else {
                    log.info("Initializing Firebase with Application Default Credentials");
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                }

                FirebaseApp.initializeApp(builder.build());
                log.info("Firebase Admin SDK initialized for project: {}", projectId);
            } else {
                log.info("Firebase Admin SDK already initialized");
            }
        } catch (IOException e) {
            log.warn("Failed to initialize Firebase Admin SDK: {}. Firebase features will be unavailable.", e.getMessage());
        }
    }

    @Bean
    public Firestore firestore() {
        try {
            FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                    .setProjectId(projectId);

            if (credentialsPath != null && !credentialsPath.isBlank()) {
                builder.setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(credentialsPath)));
            } else {
                builder.setCredentials(GoogleCredentials.getApplicationDefault());
            }

            Firestore firestore = builder.build().getService();
            log.info("Firestore client created for project: {}", projectId);
            return firestore;
        } catch (IOException e) {
            log.warn("Failed to create Firestore client: {}. Returning null.", e.getMessage());
            return null;
        }
    }
}

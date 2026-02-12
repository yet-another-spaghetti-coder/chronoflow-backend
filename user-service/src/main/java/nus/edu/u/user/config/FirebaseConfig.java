package nus.edu.u.user.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Firebase Admin SDK initialization configuration.
 * Supports both file-based and Base64-encoded credentials.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${chronoflow.firebase.credentials-path:#{null}}")
    private Resource credentialsResource;

    @Value("${chronoflow.firebase.credentials-base64:#{null}}")
    private String credentialsBase64;

    @Value("${chronoflow.firebase.project-id:}")
    private String projectId;

    @Value("${chronoflow.firebase.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void initializeFirebase() {
        if (!enabled) {
            log.info("Firebase Admin SDK is disabled");
            return;
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("Firebase Admin SDK already initialized");
                return;
            }

            InputStream serviceAccount = getCredentialsStream();
            if (serviceAccount == null) {
                log.warn("Firebase credentials not configured. Firebase authentication will not work.");
                return;
            }

            FirebaseOptions.Builder builder =
                    FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount));

            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }

            FirebaseApp.initializeApp(builder.build());
            log.info(
                    "Firebase Admin SDK initialized successfully for project: {}",
                    projectId != null && !projectId.isBlank() ? projectId : "(from credentials)");

        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}. Firebase authentication will not work.", e.getMessage());
            // Don't throw - allow app to start without Firebase if credentials are invalid
        }
    }

    private InputStream getCredentialsStream() throws IOException {
        // Try Base64-encoded credentials first (for environment variables)
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
            return new ByteArrayInputStream(decoded);
        }

        // Fall back to file-based credentials
        if (credentialsResource != null && credentialsResource.exists()) {
            return credentialsResource.getInputStream();
        }

        return null;
    }
}

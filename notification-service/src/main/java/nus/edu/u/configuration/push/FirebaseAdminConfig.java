package nus.edu.u.configuration.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FirebaseAdminConfig {

    private static volatile FirebaseApp INSTANCE;

    public static final String FIREBASE_SERVICE_ENV_NAME = "FIREBASE_SERVICE_ACCOUNT_JSON";
    private static final String PUBSUB_SERVICE_ENV_NAME = "PUB_SUB_SERVICE_ACCOUNT_JSON";
    private static final List<String> FIREBASE_MESSAGING_SCOPES =
            List.of("https://www.googleapis.com/auth/firebase.messaging");

    private final String projectId;

    public FirebaseAdminConfig(@Value("${firebase.project-id}") String projectId) {
        this.projectId = projectId;
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (INSTANCE == null) {
            synchronized (FirebaseAdminConfig.class) {
                if (INSTANCE == null) {
                    var credentials = loadGoogleCredentials();
                    var options =
                            FirebaseOptions.builder()
                                    .setCredentials(credentials)
                                    .setProjectId(projectId)
                                    .build();
                    log.info("Initializing Firebase Admin for projectId={}", projectId);
                    INSTANCE =
                            (FirebaseApp.getApps().isEmpty())
                                    ? FirebaseApp.initializeApp(options)
                                    : FirebaseApp.getInstance();
                }
            }
        }
        return INSTANCE;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    private GoogleCredentials loadGoogleCredentials() throws IOException {
        String encoded = System.getenv(FIREBASE_SERVICE_ENV_NAME);
        if (encoded == null || encoded.isBlank()) {
            encoded = System.getenv(PUBSUB_SERVICE_ENV_NAME);
            if (encoded != null && !encoded.isBlank()) {
                log.info(
                        "{} is not set; using {} for Firebase Admin",
                        FIREBASE_SERVICE_ENV_NAME,
                        PUBSUB_SERVICE_ENV_NAME);
                return decodeCredentials(encoded);
            }

            log.info(
                    "{} is not set; using application default credentials",
                    FIREBASE_SERVICE_ENV_NAME);
            return scopeForFirebaseMessaging(GoogleCredentials.getApplicationDefault());
        }
        return decodeCredentials(encoded);
    }

    private GoogleCredentials decodeCredentials(String encoded) throws IOException {
        byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
        try (var in = new java.io.ByteArrayInputStream(decoded)) {
            return scopeForFirebaseMessaging(GoogleCredentials.fromStream(in));
        }
    }

    private GoogleCredentials scopeForFirebaseMessaging(GoogleCredentials credentials) {
        return credentials.createScopedRequired()
                ? credentials.createScoped(FIREBASE_MESSAGING_SCOPES)
                : credentials;
    }
}

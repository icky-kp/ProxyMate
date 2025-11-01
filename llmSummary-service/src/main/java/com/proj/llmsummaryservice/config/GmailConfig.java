package com.proj.llmsummaryservice.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Configuration
@Slf4j
public class GmailConfig {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    // Request only the scope needed to send emails
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_SEND);

    @Value("${google.app.name}")
    private String applicationName;

    @Value("${google.credentials.file.path}")
    private String credentialsFilePath;

    @Value("${google.tokens.directory.path}")
    private String tokensDirectoryPath;

    @Value("${google.gmail.user.id}")
    private String gmailUserId; // Should be 'me' usually

    @Bean
    public Gmail gmailService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        log.info("Loading Google credentials from: {}", credentialsFilePath);
        InputStream in = new FileInputStream(credentialsFilePath);
        if (in == null) {
            throw new IOException("Resource not found: " + credentialsFilePath);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Point to the shared token directory
        File tokenDir = new File(tokensDirectoryPath);
        if (!tokenDir.exists()){
            log.warn("Token directory {} does not exist. Attempting to create.", tokensDirectoryPath);
            if (!tokenDir.mkdirs()) {
                log.error("Failed to create token directory: {}", tokensDirectoryPath);
                // Decide if you want to throw an exception or continue (will force auth flow)
            }
        }
        log.info("Using token storage directory: {}", tokenDir.getAbsolutePath());


        // Build flow. It will look for stored credentials in the token directory.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
                .setAccessType("offline") // Request refresh token
                .build();

        // Authorize. This will automatically reuse stored tokens if valid,
        // otherwise it will initiate the browser flow the first time.
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888) // Ensure this port is free, or change it
                .build();
        log.info("Attempting to authorize user: {}. Browser window might open for permission if tokens are missing/invalid.", gmailUserId);
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(gmailUserId);
        log.info("Authorization successful for user: {}", gmailUserId);

        // Create Gmail service instance.
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }
}


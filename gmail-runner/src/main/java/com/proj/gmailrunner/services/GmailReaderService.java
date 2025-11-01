package com.proj.gmailrunner.services;

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
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class GmailReaderService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_MODIFY);
    private static final Pattern MEET_LINK_PATTERN = Pattern.compile("https://meet\\.google\\.com/[a-z-]+");
    public static final String EXCHANGE_NAME = "meet-links-exchange";
    public static final String ROUTING_KEY = "meet.link.new";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${gmail.app.name}")
    private String appName;
    @Value("${gmail.tokens.directory.path}")
    private String tokensDirPath;
    @Value("${gmail.credentials.path}")
    private String credentialsFilePath;
    @Value("${gmail.user.id}")
    private String userId;
    @Value("${gmail.polling.query}")
    private String pollingQuery;

    private Gmail gmailService;

    /**
     * This method is scheduled to run every 60 seconds to check for new emails.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void pollForMeetLinks() throws Exception {
        System.out.println("Polling Gmail for new meeting links...");

        // Initialize service on first run
        if (this.gmailService == null) {
            this.gmailService = getGmailService();
        }

        ListMessagesResponse listResponse = gmailService.users().messages()
                .list(userId)
                .setQ(pollingQuery)
                .setMaxResults(1L)
                .execute();

        List<Message> messages = listResponse.getMessages();
        if (messages == null || messages.isEmpty()) {
            System.out.println("No new meeting invitations found.");
            return;
        }

        // Process the most recent unread email
        Message messageSummary = messages.get(0);
        String msgId = messageSummary.getId();
        Message fullMessage = gmailService.users().messages().get(userId, msgId).setFormat("full").execute();
        String emailBody = getEmailBody(fullMessage.getPayload());

        Matcher matcher = MEET_LINK_PATTERN.matcher(emailBody);
        if (matcher.find()) {
            String meetLink = matcher.group(0);
            System.out.println("=================================================");
            System.out.println("📤 Publishing link to RabbitMQ: " + meetLink);
            System.out.println("=================================================");

            // Send the meet link to RabbitMQ
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, meetLink);

            markAsRead(msgId);
        } else {
            System.out.println("Found an email matching query, but no meet link was extracted. Marking as read to avoid retries.");
            markAsRead(msgId);
        }
    }

    /**
     * Authenticates with Google and creates a Gmail service object.
     */
    private Gmail getGmailService() throws Exception {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Load client secrets from the credentials file.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(Files.newInputStream(Paths.get(credentialsFilePath))));

        // Build the authorization flow with force approval prompt
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(tokensDirPath)))
                .setAccessType("offline")
                .setApprovalPrompt("force")  // This forces the generation of refresh token
                .build();

        // Authorize and get credentials
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();
        
        // Clear credentials if they exist
        flow.getCredentialDataStore().clear();
        
        // Get new credentials
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize(userId);

        return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(appName)
                .build();
    }

    /**
     * Recursively parses the email payload to find the body content.
     */
    private String getEmailBody(MessagePart payload) {
        if (payload.getMimeType().equalsIgnoreCase("text/plain") || payload.getMimeType().equalsIgnoreCase("text/html")) {
            if (payload.getBody() != null && payload.getBody().getData() != null) {
                return new String(Base64.getUrlDecoder().decode(payload.getBody().getData()), StandardCharsets.UTF_8);
            }
        }

        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                String body = getEmailBody(part);
                if (body != null && !body.isEmpty()) {
                    return body;
                }
            }
        }
        return ""; // Return empty string if no body is found
    }

    /**
     * Marks a specific email as read by removing the 'UNREAD' label.
     */
    private void markAsRead(String messageId) throws IOException {
        ModifyMessageRequest mods = new ModifyMessageRequest().setRemoveLabelIds(Collections.singletonList("UNREAD"));
        gmailService.users().messages().modify(userId, messageId, mods).execute();
        System.out.println("Message ID: " + messageId + " marked as read.");
    }
}
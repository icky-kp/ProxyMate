package com.proj.llmsummaryservice.services;

import com.proj.llmsummaryservice.dto.GeminiResponseDto;
import com.proj.llmsummaryservice.dto.TranscriptSegmentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SummaryService {

    private final WebClient webClient;
    private final EmailService emailService;
    private final String geminiApiKey;
    private final String geminiApiUrl;

    // In-memory storage for transcript segments, grouped by meetingId
    private final Map<String, List<String>> meetingTranscripts = new ConcurrentHashMap<>();

    // Constructor Injection
    public SummaryService(WebClient webClient,
                          EmailService emailService,
                          @Value("${gemini.api.key}") String geminiApiKey,
                          @Value("${gemini.api.url}") String geminiApiUrl) {
        this.webClient = webClient;
        this.emailService = emailService;
        this.geminiApiKey = geminiApiKey;
        // Construct the URL with the API key
        this.geminiApiUrl = geminiApiUrl + "?key=" + geminiApiKey;
    }

    /**
     * Adds a received transcript segment to the in-memory store.
     */
    public void addTranscriptSegment(TranscriptSegmentDto segment) {
        if (segment.meetingId() == null || segment.speaker() == null || segment.content() == null) {
            log.warn("Received invalid transcript segment: {}", segment);
            return;
        }
        // Store the transcript segment with speaker information
        meetingTranscripts.computeIfAbsent(segment.meetingId(), k -> new ArrayList<>())
                .add(String.format("[%s]: %s", segment.speaker(), segment.content()));
        log.debug("Added segment for meeting: {}", segment.meetingId());
    }

    /**
     * Called when the final segment marker is received. Assembles the full transcript
     * and triggers the summarization process.
     */
    public void processAndSummarizeMeeting(String meetingId) {
        log.info("Final segment received for meeting: {}. Processing for summarization.", meetingId);
        List<String> segments = meetingTranscripts.remove(meetingId); // Atomically remove from map

        if (segments == null || segments.isEmpty()) {
            log.warn("No transcript segments found for meeting: {} upon finalization.", meetingId);
            return; // Do not proceed if there is no transcript
        }

        String fullTranscript = String.join("\n", segments);
        log.info("Assembled full transcript for meeting: {}. Length: {} characters.", meetingId, fullTranscript.length());

        // Call the method to get the summary and send the email
        callGeminiApiAndSendEmail(meetingId, fullTranscript);
    }

    /**
     * Makes the API call to the Gemini API and then triggers the email service.
     */
    private void callGeminiApiAndSendEmail(String meetingId, String fullTranscript) {
        log.info("Calling Gemini API for meeting: {}", meetingId);

        // Define the prompt for the LLM
        String prompt = "Please provide a concise summary of the following meeting transcript. " +
                "Also, identify a list of key decisions made and any action items assigned. " +
                "Format the output clearly with markdown headings for 'Summary', 'Decisions', and 'Action Items'.\n\n" +
                "Transcript:\n" + fullTranscript;

        // Construct the Gemini API request body structure
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        // Make the asynchronous call using WebClient
        webClient.post()
                .uri(geminiApiUrl) // The URL already includes the API key
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponseDto.class) // Deserialize into our DTO
                .doOnError(error -> log.error("Gemini API call failed for meeting {}: {}", meetingId, error.getMessage(), error))
                .subscribe(response -> {
                    String summary = parseSummaryFromResponse(response);
                    if (summary != null) {
                        log.info("===== GEMINI SUMMARY for Meeting: {} =====\n{}", meetingId, summary);

                        // --- Send Email to the specified recipient ---
                        String recipientEmail = "kaustav.k48@gmail.com"; // Set to the requested email
                        emailService.sendSummaryEmail(recipientEmail, meetingId, summary, fullTranscript);
                        // --- End Email Sending ---

                    } else {
                        log.warn("Could not extract summary from Gemini response for meeting: {}", meetingId);
                        log.debug("Raw Gemini Response: {}", response); // Log raw response for debugging
                    }
                }, error -> {
                    // Fallback error logging for the subscription
                    log.error("Unhandled error in Gemini API call subscription for meeting {}: {}", meetingId, error.getMessage(), error);
                });
    }

    /**
     * Safely extracts the summary text from the Gemini API response DTO.
     */
    private String parseSummaryFromResponse(GeminiResponseDto response) {
        try {
            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                GeminiResponseDto.Candidate candidate = response.getCandidates().get(0);
                if (candidate.getContent() != null && candidate.getContent().getParts() != null && !candidate.getContent().getParts().isEmpty()) {
                    return candidate.getContent().getParts().get(0).getText();
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
        }
        return null; // Return null if parsing fails at any step
    }
}


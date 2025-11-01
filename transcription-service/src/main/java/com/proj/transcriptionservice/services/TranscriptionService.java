package com.proj.transcriptionservice.services;

import com.proj.transcriptionservice.config.RabbitMQConfig;
import com.proj.transcriptionservice.dto.AudioChunkDto;
import com.proj.transcriptionservice.dto.TranscriptSegmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final TranscribeStreamingAsyncClient transcribeClient;
    private final RabbitTemplate rabbitTemplate; // Inject RabbitTemplate
    private final Map<String, AudioStreamPublisher> activeStreams = new ConcurrentHashMap<>();

    public void processAudioChunk(AudioChunkDto chunk) {
        String meetingId = chunk.meetingId();

        if (chunk.isFinalChunk()) {
            log.info("Received final audio chunk marker for meetingId: {}. Closing transcription stream.", meetingId);
            AudioStreamPublisher publisher = activeStreams.remove(meetingId);
            if (publisher != null) {
                publisher.close(); // This will trigger the onComplete handler below
            }
            // Do NOT send the final transcript message here, wait for AWS onComplete
            return;
        }

        AudioStreamPublisher publisher = activeStreams.computeIfAbsent(meetingId, this::startTranscriptionStream);

        if (chunk.audioBytes() != null && chunk.audioBytes().length > 0) {
            publisher.publish(chunk.audioBytes());
            // Log less frequently to avoid flooding
            if (chunk.sequenceId() % 100 == 0) {
                log.debug("Sent chunk #{} for meetingId: {} to AWS Transcribe.", chunk.sequenceId(), meetingId);
            }
        }
    }

    private AudioStreamPublisher startTranscriptionStream(String meetingId) {
        log.info("Starting new transcription stream for meetingId: {}", meetingId);

        AudioStreamPublisher publisher = new AudioStreamPublisher();

        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(LanguageCode.EN_US)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(16000)
                .showSpeakerLabel(true) // Enable speaker diarization
                .build();

        StartStreamTranscriptionResponseHandler handler = StartStreamTranscriptionResponseHandler.builder()
                .onResponse(r -> log.info("Successfully established transcription stream for meetingId: {}", meetingId))
                .onError(e -> log.error("Error during transcription stream for meetingId: {}", meetingId, e))
                .onComplete(() -> {
                    // This is triggered when the AWS stream *itself* completes (either normally or via error/close).
                    // Send the final marker message for this meeting.
                    log.info("Transcription stream completed for meetingId: {}. Sending final transcript marker.", meetingId);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.TRANSCRIPT_FINAL_ROUTING_KEY,
                            TranscriptSegmentDto.createFinalMessage(meetingId));
                    // Clean up map entry just in case it wasn't removed on final audio chunk
                    activeStreams.remove(meetingId);
                })
                .subscriber(event -> {
                    // Process events from AWS Transcribe stream
                    if (event instanceof TranscriptEvent) {
                        handleTranscriptEvent((TranscriptEvent) event, meetingId);
                    }
                })
                .build();

        CompletableFuture<Void> result = transcribeClient.startStreamTranscription(request, publisher, handler);
        // Handle potential synchronous errors during stream initiation (e.g., config error)
        result.exceptionally(e -> {
            log.error("Failed to start transcription stream for meetingId: {}", meetingId, e);
            activeStreams.remove(meetingId); // Clean up if start failed
            // Optionally send a final message immediately if start fails catastrophically
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.TRANSCRIPT_FINAL_ROUTING_KEY,
                    TranscriptSegmentDto.createFinalMessage(meetingId));
            return null;
        });

        return publisher;
    }

    private void handleTranscriptEvent(TranscriptEvent transcriptEvent, String meetingId) {
        List<Result> results = transcriptEvent.transcript().results();
        if (results.isEmpty() || results.get(0).isPartial()) {
            return; // Ignore partial results for now
        }

        Result result = results.get(0);
        Alternative bestAlternative = result.alternatives().get(0);

        // Group words by speaker label to create a turn-by-turn transcript
        Map<String, List<Item>> wordsBySpeaker = bestAlternative.items().stream()
                .filter(item -> item.type() == ItemType.PRONUNCIATION && item.speaker() != null) // Only pronunciation items have speaker
                .collect(Collectors.groupingBy(Item::speaker));

        // Publish each speaker's segment as a separate message
        for (Map.Entry<String, List<Item>> entry : wordsBySpeaker.entrySet()) {
            String speaker = entry.getKey(); // e.g., "spk_0"
            String content = entry.getValue().stream()
                    .map(Item::content)
                    .collect(Collectors.joining(" "));

            if (!content.isBlank()) {
                TranscriptSegmentDto segmentDto = new TranscriptSegmentDto(meetingId, speaker, content, false);
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.TRANSCRIPT_SEGMENT_ROUTING_KEY, segmentDto);
                log.info("Published transcript segment for meeting [{}], speaker [{}]: {}", meetingId, speaker, content);
            }
        }
    }
}

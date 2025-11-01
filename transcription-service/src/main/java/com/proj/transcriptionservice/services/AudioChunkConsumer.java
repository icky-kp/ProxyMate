package com.proj.transcriptionservice.services;

import com.proj.transcriptionservice.config.RabbitMQConfig;
import com.proj.transcriptionservice.dto.AudioChunkDto;
import com.proj.transcriptionservice.services.TranscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioChunkConsumer {

    private final TranscriptionService transcriptionService;

    @RabbitListener(queues = RabbitMQConfig.AUDIO_CHUNKS_QUEUE)
    public void receiveAudioChunk(AudioChunkDto chunk) {
        // Log less frequently
        if (chunk.sequenceId() % 100 == 0 || chunk.isFinalChunk()) {
            log.debug("Received chunk #{} for meetingId: {}", chunk.sequenceId(), chunk.meetingId());
        }
        try {
            transcriptionService.processAudioChunk(chunk);
        } catch (Exception e) {
            log.error("Failed to process audio chunk for meetingId: {}", chunk.meetingId(), e);
        }
    }
}

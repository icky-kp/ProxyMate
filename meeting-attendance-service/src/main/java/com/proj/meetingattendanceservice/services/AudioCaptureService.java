package com.proj.meetingattendanceservice.services;

import com.proj.meetingattendanceservice.config.RabbitMQConfig;
import com.proj.meetingattendanceservice.dto.AudioChunkDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioCaptureService {

    private final RabbitTemplate rabbitTemplate;

    /**
     * CHANGED: This method now reads audio in small, frequent chunks suitable for AWS Transcribe.
     */
    public void processAndPublishAudio(InputStream audioStream, String meetingId) {
        log.info("Starting to process FFmpeg audio stream for meetingId: {}", meetingId);
        // A buffer size of 3200 bytes corresponds to 100ms of 16kHz, 16-bit, mono PCM audio.
        // (16000 samples/sec * 2 bytes/sample * 0.1 sec = 3200 bytes)
        // This is within the AWS recommended 50ms-200ms range.
        final int bufferSize = 3200;
        byte[] buffer = new byte[bufferSize];
        AtomicInteger sequenceId = new AtomicInteger(1);

        try {
            int bytesRead;
            // The read(buffer) method will read up to the buffer's size and return.
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    byte[] audioChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                    AudioChunkDto chunkDto = new AudioChunkDto(meetingId, audioChunk, sequenceId.getAndIncrement(), false);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "audio.chunk", chunkDto);
                    log.info("✅ Published audio chunk #{} ({} bytes) for meeting {}", chunkDto.getSequenceId(), chunkDto.getAudioBytes().length, meetingId);
                }
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Stream closed")) {
                log.info("FFmpeg audio stream closed as expected.");
            } else {
                log.error("Error reading from FFmpeg audio stream for meeting {}", meetingId, e);
            }
        }
    }

    public void sendFinalChunk(String meetingId) {
        if (meetingId != null) {
            AudioChunkDto finalChunk = new AudioChunkDto(meetingId, new byte[0], -1, true);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "audio.chunk", finalChunk);
            log.info("Sent final chunk for meeting {}", meetingId);
        }
    }
}

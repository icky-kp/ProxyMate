package com.proj.llmsummaryservice.services;

import com.proj.llmsummaryservice.config.RabbitMQConfig;
import com.proj.llmsummaryservice.dto.TranscriptSegmentDto;
import com.proj.llmsummaryservice.services.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptConsumer {

    private final SummaryService summaryService;

    @RabbitListener(queues = RabbitMQConfig.TRANSCRIPTS_QUEUE)
    public void receiveTranscriptSegment(TranscriptSegmentDto segment) {
        try {
            if (segment.isFinalSegment()) {
                log.info("Received final transcript marker for meeting: {}", segment.meetingId());
                summaryService.processAndSummarizeMeeting(segment.meetingId());
            } else {
                log.debug("Received transcript segment for meeting: {}, speaker: {}", segment.meetingId(), segment.speaker());
                summaryService.addTranscriptSegment(segment);
            }
        } catch (Exception e) {
            log.error("Error processing transcript segment for meeting {}: {}", segment.meetingId(), e.getMessage(), e);
            // Consider adding dead-letter queue logic here for resilience
        }
    }
}

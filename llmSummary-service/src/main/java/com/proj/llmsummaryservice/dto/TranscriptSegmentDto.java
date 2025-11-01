package com.proj.llmsummaryservice.dto;

import java.io.Serializable;

/**
 * Represents a single speaker's turn or segment from the transcription.
 * Must match the DTO in the transcription-service exactly.
 */
public record TranscriptSegmentDto(
        String meetingId,
        String speaker,     // e.g., "spk_0", "spk_1"
        String content,     // The actual transcribed text for this segment
        boolean isFinalSegment // Flag to signal the end of the meeting transcription
) implements Serializable {
}

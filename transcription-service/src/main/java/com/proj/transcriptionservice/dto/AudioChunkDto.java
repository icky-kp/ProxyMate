package com.proj.transcriptionservice.dto;

import java.io.Serializable;

/**
 * Represents a single chunk of audio from a meeting.
 * Using a record for an immutable data carrier.
 */
public record AudioChunkDto(
        String meetingId,
        byte[] audioBytes,
        int sequenceId,
        boolean isFinalChunk
) implements Serializable {
}


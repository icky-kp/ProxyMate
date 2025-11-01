package com.proj.meetingattendanceservice.dto;

import java.io.Serializable;
import java.util.Arrays;

// CHANGED: Converted from a record to a class and implemented Serializable
public class AudioChunkDto implements Serializable {

    private String meetingId;
    private byte[] audioBytes;
    private int sequenceId;
    private boolean isFinalChunk;

    // A no-argument constructor is required for JSON deserialization
    public AudioChunkDto() {
    }

    public AudioChunkDto(String meetingId, byte[] audioBytes, int sequenceId, boolean isFinalChunk) {
        this.meetingId = meetingId;
        this.audioBytes = audioBytes;
        this.sequenceId = sequenceId;
        this.isFinalChunk = isFinalChunk;
    }

    // Getters and Setters are required for JSON serialization/deserialization
    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public byte[] getAudioBytes() {
        return audioBytes;
    }

    public void setAudioBytes(byte[] audioBytes) {
        this.audioBytes = audioBytes;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(int sequenceId) {
        this.sequenceId = sequenceId;
    }

    public boolean isFinalChunk() {
        return isFinalChunk;
    }

    public void setFinalChunk(boolean finalChunk) {
        isFinalChunk = finalChunk;
    }

    @Override
    public String toString() {
        return "AudioChunkDto{" +
                "meetingId='" + meetingId + '\'' +
                ", audioBytes=" + (audioBytes != null ? audioBytes.length + " bytes" : "null") +
                ", sequenceId=" + sequenceId +
                ", isFinalChunk=" + isFinalChunk +
                '}';
    }
}
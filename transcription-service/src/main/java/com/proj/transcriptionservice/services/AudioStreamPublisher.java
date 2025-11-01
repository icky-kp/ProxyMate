package com.proj.transcriptionservice.services;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A reactive streams publisher for audio data with a heartbeat mechanism.
 */
public class AudioStreamPublisher implements Publisher<AudioStream> {

    private final ExecutorService publishingExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    private Subscriber<? super AudioStream> subscriber;
    private final AtomicLong lastAudioPublishedTime = new AtomicLong(System.currentTimeMillis());

    @Override
    public void subscribe(Subscriber<? super AudioStream> s) {
        this.subscriber = s;
        s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {}

            @Override
            public void cancel() {}
        });
        startHeartbeat();
    }

    public void publish(byte[] audioBytes) {
        publishingExecutor.submit(() -> {
            if (subscriber != null) {
                AudioEvent audioEvent = AudioEvent.builder()
                        .audioChunk(SdkBytes.fromByteArray(audioBytes))
                        .build();
                subscriber.onNext(audioEvent);
                lastAudioPublishedTime.set(System.currentTimeMillis());
            }
        });
    }

    public void close() {
        // Ensure graceful shutdown of executors
        publishingExecutor.submit(() -> {
            if (subscriber != null) {
                subscriber.onComplete();
            }
        });
        shutdownExecutors();
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            // Check if publishingExecutor is still active before sending heartbeat
            if (publishingExecutor.isShutdown() || publishingExecutor.isTerminated()){
                return; // Stop heartbeat if main publisher is closed
            }

            long now = System.currentTimeMillis();
            if (now - lastAudioPublishedTime.get() > 5000) { // Send heartbeat if no audio for 5s
                if (subscriber != null) {
                    AudioEvent heartbeatEvent = AudioEvent.builder()
                            .audioChunk(SdkBytes.fromByteArray(new byte[0])) // Empty audio chunk
                            .build();
                    // Use the publishing executor to maintain order if needed, though for heartbeat it might not be critical
                    publishingExecutor.submit(() -> {
                        if(subscriber != null) subscriber.onNext(heartbeatEvent);
                    });
                }
            }
        }, 5, 5, TimeUnit.SECONDS); // Check every 5 seconds
    }

    private void shutdownExecutors() {
        publishingExecutor.shutdown();
        heartbeatExecutor.shutdown();
        try {
            if (!publishingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                publishingExecutor.shutdownNow();
            }
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            publishingExecutor.shutdownNow();
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

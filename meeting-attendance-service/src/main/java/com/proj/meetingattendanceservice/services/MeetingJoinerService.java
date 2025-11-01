package com.proj.meetingattendanceservice.services;

import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingJoinerService {

    private final AudioCaptureService audioCaptureService;

    @Value("${app.browser.headless}")
    private boolean isHeadless;

    @Value("${app.audio.total-meeting-duration-minutes}")
    private int totalMeetingDurationMinutes;

    @Async
    public void joinMeet(String meetLink) {
        String audioSource = "alsa_output.pci-0000_00_1f.3-platform-skl_hda_dsp_generic.HiFi__hw_sofhdadsp__sink.monitor";
        String chromiumUserProfilePath = "/home/kaustavdeb/.config/botsingh-chrome";

        BrowserContext context = null;
        Process ffmpegProcess = null;
        ExecutorService executor = Executors.newFixedThreadPool(2); // One for audio, one for logs
        String meetingId = UUID.randomUUID().toString();

        log.info("🚀 Initializing browser to join: {}", meetLink);
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(isHeadless)
                    .setSlowMo(100);

            context = playwright.chromium().launchPersistentContext(Paths.get(chromiumUserProfilePath), options);
            Page page = context.newPage();

            log.info("Navigating to meet link...");
            page.navigate(meetLink, new Page.NavigateOptions().setTimeout(90000));

            log.info("Looking for 'Join now' or 'Ask to join' button...");
            Locator joinButton = page.locator("button:has-text('Join now'), button:has-text('Ask to join')");
            joinButton.waitFor(new Locator.WaitForOptions().setTimeout(60000));
            joinButton.click();
            log.info("✅ Successfully sent join request for meetingId: {}", meetingId);

            log.info("Starting system audio capture with FFmpeg for source: {}", audioSource);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-f", "pulse",
                    "-i", audioSource,
                    "-ac", "1",
                    "-ar", "16000",
                    "-f", "s16le",
                    "-"
            );
            ffmpegProcess = processBuilder.start();
            log.info("FFmpeg process started successfully.");

            // Submit the audio processing task
            InputStream ffmpegOutputStream = ffmpegProcess.getInputStream();
            executor.submit(() -> audioCaptureService.processAndPublishAudio(ffmpegOutputStream, meetingId));

            // NEW: Submit a separate task to log FFmpeg's error stream
            InputStream ffmpegErrorStream = ffmpegProcess.getErrorStream();
            executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegErrorStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.warn("[FFMPEG LOG]: {}", line);
                    }
                } catch (Exception e) {
                    log.error("Error reading from FFmpeg error stream", e);
                }
            });

            log.info("Bot will attend and record for {} minutes...", totalMeetingDurationMinutes);
            Thread.sleep((long) totalMeetingDurationMinutes * 60 * 1000);

        } catch (Exception e) {
            log.error("A critical error occurred during the meeting lifecycle: {}", meetLink, e);
            throw new RuntimeException("Failed to join or record meeting", e);
        } finally {
            log.info("Meeting duration is over. Cleaning up resources for meetingId: {}", meetingId);

            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                log.info("Stopping FFmpeg process...");
                ffmpegProcess.destroy();
            }

            executor.shutdownNow();

            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor threads did not terminate gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            audioCaptureService.sendFinalChunk(meetingId);

            if (context != null) {
                context.close();
                log.info("Browser context closed.");
            }
        }
    }
}


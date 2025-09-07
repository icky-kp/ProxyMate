package com.proj.meetingattendanceservice.services;

import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Paths;

@Service
public class MeetingJoinerService {

    @Value("${app.browser.headless}")
    private boolean isHeadless;

    public void joinMeet(String meetLink) {
        // --- ACTION REQUIRED: UPDATE THIS PATH ---
        // 1. Open Firefox, navigate to the address: about:profiles
        // 2. Find the profile in use (it usually says "This is the profile in use").
        // 3. Copy the "Root Directory" path.
        // 4. Paste the path here. Use double backslashes (\) on Windows.
        String firefoxUserProfilePath = "/home/kaustavdeb/.cache/mozilla/firefox/a92hd52z.botsingh";
        // Example for Linux: "/home/user/.mozilla/firefox/xxxxxxxx.default-release"
        // Example for Windows: "C:\\Users\\User\\AppData\\Roaming\\Mozilla\\Firefox\\Profiles\\xxxxxxxx.default-release"
        // -----------------------------------------

        System.out.println("🚀 Initializing browser using persistent Firefox profile...");
        try (Playwright playwright = Playwright.create()) {
            // Launch a persistent context using your existing browser data.
            // This makes the browser session look authentic and should bypass security checks.

            // --- FIX: Use the correct options object for launchPersistentContext ---
            BrowserContext context = playwright.firefox().launchPersistentContext(
                    Paths.get(firefoxUserProfilePath),
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(isHeadless).setSlowMo(100)
            );

            // The browser should already be logged into your Google account.
            Page page = context.newPage();

            System.out.println("Navigating to meet link: " + meetLink);
            page.navigate(meetLink);
            System.out.println("Navigation successful. Waiting for meet lobby to load...");

            // Directly look for and click the "Join now" or "Ask to join" button
            System.out.println("Looking for 'Join now' or 'Ask to join' button...");
            Locator joinButton = page.locator("button:has-text('Join now'), button:has-text('Ask to join')");
            joinButton.waitFor(new Locator.WaitForOptions().setTimeout(60000)); // Wait up to 60 seconds for the join button
            System.out.println("Clicking join button...");
            joinButton.click();

            System.out.println("✅ Successfully sent join request! The bot will now attend for 60 minutes.");

            Thread.sleep(60 * 60 * 1000);

            System.out.println("Meeting duration is over. Closing browser.");
            // Note: Closing the persistent context will close all browser windows associated with it.
            context.close();
        } catch (Exception e) {
            System.err.println("An error occurred during browser automation.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}


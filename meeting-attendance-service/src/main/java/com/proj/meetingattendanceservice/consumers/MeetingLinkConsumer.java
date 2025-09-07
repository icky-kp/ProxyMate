package com.proj.meetingattendanceservice.consumers;

import com.proj.meetingattendanceservice.services.MeetingJoinerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MeetingLinkConsumer {

    public static final String QUEUE_NAME = "meet-links-queue";

    @Autowired
    private MeetingJoinerService meetingJoinerService;

    @RabbitListener(queues = QUEUE_NAME)
    public void receiveMeetLink(String meetLink) {
        System.out.println("📥 Received link from queue: " + meetLink);
        try {
            meetingJoinerService.joinMeet(meetLink);
        } catch (Exception e) {
            System.err.println("Failed to join meeting: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
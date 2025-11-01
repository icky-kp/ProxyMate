package com.proj.meetingattendanceservice.consumers;

import com.proj.meetingattendanceservice.config.RabbitMQConfig;
import com.proj.meetingattendanceservice.services.MeetingJoinerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingLinkConsumer {

    private final MeetingJoinerService meetingJoinerService;

    @RabbitListener(queues = RabbitMQConfig.INBOUND_QUEUE_NAME)
    public void receiveMeetLink(String meetLink) {
        log.info("📥 Received link from queue: {}", meetLink);
        try {
            meetingJoinerService.joinMeet(meetLink);
        } catch (Exception e) {
            log.error("Failed to initiate meeting join process for link: {}", meetLink, e);
        }
    }
}
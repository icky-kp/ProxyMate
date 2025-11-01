package com.proj.meetingattendanceservice;

import com.proj.meetingattendanceservice.services.MeetingJoinerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class MeetingAttendanceServiceApplication {
	private static final Logger logger = LoggerFactory.getLogger(MeetingAttendanceServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(MeetingAttendanceServiceApplication.class, args);
		logger.info("Meeting Attendance Service started successfully");
	}
//	@Bean
//	@Profile("test")
//	public CommandLineRunner testRunner(MeetingJoinerService joinerService) {
//		return args -> {
//			String testLink = "https://meet.google.com/cnq-zeui-ioi";
//			System.out.println("==============================================");
//			System.out.println("          RUNNING IN TEST MODE          ");
//			System.out.println("==============================================");
//			System.out.println("Attempting to join hardcoded link: " + testLink);
//			joinerService.joinMeet(testLink);
//			System.out.println("==============================================");
//			System.out.println("        TEST RUN FINISHED. SHUTTING DOWN.     ");
//			System.out.println("==============================================");
//			// Exit after test run to prevent the app from hanging
//			System.exit(0);
//		};
//	}

}

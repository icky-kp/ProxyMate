package com.proj.transcriptionservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AUDIO_CHUNKS_QUEUE = "audio-chunks-queue";
    public static final String TRANSCRIPTS_QUEUE = "transcripts-queue"; // New queue
    public static final String EXCHANGE_NAME = "meeting-exchange"; // Using the same exchange

    public static final String TRANSCRIPT_SEGMENT_ROUTING_KEY = "transcript.segment";
    public static final String TRANSCRIPT_FINAL_ROUTING_KEY = "transcript.final";

    @Bean
    public Queue audioChunksQueue() {
        return new Queue(AUDIO_CHUNKS_QUEUE, true); // Durable queue
    }

    // Bean for the new transcripts queue
    @Bean
    public Queue transcriptsQueue() {
        return new Queue(TRANSCRIPTS_QUEUE, true); // Durable queue
    }

    // Exchange remains the same
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // Binding for audio chunks (no change needed if meeting-attendance-service uses 'audio.chunk')
    // Assuming meeting-attendance-service publishes with routing key "audio.chunk"
    @Bean
    public Binding audioBinding(Queue audioChunksQueue, TopicExchange exchange) {
        return BindingBuilder.bind(audioChunksQueue).to(exchange).with("audio.#");
    }

    // Binding for transcript segments (used by this service as a producer)
    @Bean
    public Binding transcriptBinding(Queue transcriptsQueue, TopicExchange exchange) {
        // We will bind the queue to listen for both segment and final messages
        return BindingBuilder.bind(transcriptsQueue).to(exchange).with("transcript.#");
    }


    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

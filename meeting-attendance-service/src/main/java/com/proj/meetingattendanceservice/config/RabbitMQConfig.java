package com.proj.meetingattendanceservice.config;

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

    public static final String INBOUND_QUEUE_NAME = "meet-links-queue";
    public static final String OUTBOUND_QUEUE_NAME = "audio-chunks-queue";
    public static final String EXCHANGE_NAME = "meeting-exchange";

    // NEW: This bean configures Spring to use a JSON message converter.
    // It will automatically convert our DTOs to and from JSON.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue inboundQueue() {
        return new Queue(INBOUND_QUEUE_NAME, true);
    }

    @Bean
    public Queue outboundQueue() {
        return new Queue(OUTBOUND_QUEUE_NAME, true);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding inboundBinding(Queue inboundQueue, TopicExchange exchange) {
        return BindingBuilder.bind(inboundQueue).to(exchange).with("link.#");
    }

    @Bean
    public Binding outboundBinding(Queue outboundQueue, TopicExchange exchange) {
        return BindingBuilder.bind(outboundQueue).to(exchange).with("audio.#");
    }
}
package com.proj.llmsummaryservice.config;

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

    public static final String TRANSCRIPTS_QUEUE = "transcripts-queue";
    public static final String EXCHANGE_NAME = "meeting-exchange";

    @Bean
    public Queue transcriptsQueue() {
        // Declare the queue - needs to match the producer's declaration
        return new Queue(TRANSCRIPTS_QUEUE, true);
    }

    @Bean
    public TopicExchange exchange() {
        // Declare the exchange - needs to match the producer's declaration
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding transcriptBinding(Queue transcriptsQueue, TopicExchange exchange) {
        // Bind the queue to listen for transcript messages
        return BindingBuilder.bind(transcriptsQueue).to(exchange).with("transcript.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Use JSON converter to deserialize incoming messages
        return new Jackson2JsonMessageConverter();
    }
}

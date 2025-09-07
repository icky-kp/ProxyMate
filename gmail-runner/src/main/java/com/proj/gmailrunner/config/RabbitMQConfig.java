package com.proj.gmailrunner.config; // Make sure this package name matches your project's

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "meet-links-exchange";
    public static final String QUEUE_NAME = "meet-links-queue";
    public static final String ROUTING_KEY = "meet.link.new";

    /**
     * Defines the durable queue that will hold the meet links.
     * @return a Queue bean.
     */
    @Bean
    Queue queue() {
        // durable=true means the queue will survive a broker restart
        return new Queue(QUEUE_NAME, true);
    }

    /**
     * Defines the topic exchange that will receive messages from the producer.
     * @return a TopicExchange bean.
     */
    @Bean
    TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    /**
     * Creates a binding between the queue and the exchange, using the routing key.
     * This tells the exchange to forward messages with this routing key to our queue.
     * @param queue the queue bean
     * @param exchange the exchange bean
     * @return a Binding bean.
     */
    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }
}

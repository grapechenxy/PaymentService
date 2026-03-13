package com.example.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Value("${payments.exchange}")
    private String exchangeName;

    @Value("${payments.queue}")
    private String queueName;

    @Value("${payments.dlq}")
    private String dlqName;

    @Value("${payments.routing-key}")
    private String routingKey;

    @Value("${payments.dl-routing-key}")
    private String dlRoutingKey;

    @Bean
    public DirectExchange paymentsExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue paymentsQueue() {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(exchangeName)
                .deadLetterRoutingKey(dlRoutingKey)
                //.maxLength(10000)
                .build();
    }

    @Bean
    public Queue paymentsDlq() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding paymentsBinding() {
        return BindingBuilder.bind(paymentsQueue()).to(paymentsExchange()).with(routingKey);
    }

    @Bean
    public Binding paymentsDlqBinding() {
        return BindingBuilder.bind(paymentsDlq()).to(paymentsExchange()).with(dlRoutingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(4);
        // normalize missing headers from seeded messages
        factory.setAfterReceivePostProcessors(message -> {
            MessageProperties props = message.getMessageProperties();
            if (props.getPriority() == null) {
                props.setPriority(0);
            }
            if (props.getContentType() == null) {
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            }
            return message;
        });
        return factory;
    }
}

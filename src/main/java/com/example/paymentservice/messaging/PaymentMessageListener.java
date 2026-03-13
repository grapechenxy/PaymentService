package com.example.paymentservice.messaging;

import com.example.paymentservice.model.PaymentMessage;
import com.example.paymentservice.service.PaymentProcessingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageListener.class);

    private final PaymentProcessingService processingService;

    @RabbitListener(queues = "${payments.queue}", containerFactory = "rabbitListenerContainerFactory")
    public void handlePaymentMessage(PaymentMessage payload, Channel channel, Message message) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MDC.put("messageId", payload.getId());
        MDC.put("userId", payload.getUserId());
        if (payload.getCorrelationId() != null) {
            MDC.put("correlationId", payload.getCorrelationId());
        }
        try {
            var result = processingService.handleMessage(payload);
            switch (result.getResultType()) {
                case SUCCESS, SCHEDULED_RETRY -> {
                    channel.basicAck(deliveryTag, false);
                    log.info("Processed message {}, outcome {}", payload.getId(), result.getInfo());
                }
                case FATAL -> {
                    if ("validation-failed".equals(result.getInfo())) {
                        channel.basicReject(deliveryTag, false);
                    } else {
                        channel.basicAck(deliveryTag, false);
                    }
                    log.warn("Fatal processing for message {}: {}", payload.getId(), result.getError());
                }
            }
        } finally {
            MDC.clear();
        }
    }
}

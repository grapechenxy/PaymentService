package com.example.paymentservice.service;

import com.example.paymentservice.model.PaymentAttempt;
import com.example.paymentservice.model.PaymentAttemptStatus;
import com.example.paymentservice.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final PaymentAttemptRepository attemptRepository;
    private final PaymentProcessingService processingService;
    private final PaymentClient paymentClient;

    @Value("${payments.retry.scheduler-cron:0 */5 * * * *}")
    private String cronExpression;

    @Scheduled(cron = "${payments.retry.scheduler-cron:0 */5 * * * *}")
    public void processRetries() {
        List<PaymentAttempt> dueAttempts = attemptRepository.findByStatusAndNextAttemptAtBefore(
                PaymentAttemptStatus.PENDING_RETRY, Instant.now());

        if (dueAttempts.isEmpty()) {
            return;
        }

        for (PaymentAttempt attempt : dueAttempts) {
            var result = processingService.retry(attempt);
            log.info("Retry attempt {} for paymentId {} result {}", attempt.getAttemptCount() + 1,
                    attempt.getPaymentId(), result.getResultType());
        }
    }
}

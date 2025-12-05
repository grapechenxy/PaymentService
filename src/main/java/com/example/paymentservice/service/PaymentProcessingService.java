package com.example.paymentservice.service;

import com.example.paymentservice.model.PaymentAttempt;
import com.example.paymentservice.model.PaymentAttemptStatus;
import com.example.paymentservice.model.PaymentCallResult;
import com.example.paymentservice.model.PaymentMessage;
import com.example.paymentservice.repository.PaymentAttemptRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentValidationService validationService;
    private final PaymentClient paymentClient;
    private final PaymentAttemptRepository attemptRepository;

    @Value("${payments.retry.interval-days:1}")
    private int retryIntervalDays;

    @Value("${payments.retry.jitter-seconds:0}")
    private int jitterSeconds;

    @Value("${payments.retry.max-total-attempts:4}")
    private int maxTotalAttempts;

    public ProcessingResult handleMessage(PaymentMessage message) {
        Optional<PaymentAttempt> existing = attemptRepository.findById(message.getId());
        if (existing.isPresent() && (existing.get().getStatus() == PaymentAttemptStatus.COMPLETED
                || existing.get().getStatus() == PaymentAttemptStatus.FAILED)) {
            log.info("Duplicate message {} ignored; already {}", message.getId(), existing.get().getStatus());
            return ProcessingResult.builder()
                    .resultType(ResultType.SUCCESS)
                    .info("duplicate-ignored")
                    .build();
        }

        var validationErrors = validationService.validate(message);
        if (!validationErrors.isEmpty()) {
            return ProcessingResult.builder()
                    .resultType(ResultType.FATAL)
                    .info("validation-failed")
                    .error(String.join("; ", validationErrors))
                    .build();
        }

        PaymentCallResult callResult = paymentClient.sendPayment(message);
        return handleResult(message, callResult, existing.orElse(null));
    }

    public ProcessingResult retry(PaymentAttempt attempt) {
        PaymentMessage message = PaymentMessage.builder()
                .id(attempt.getId())
                .userId(attempt.getUserId())
                .paymentId(attempt.getPaymentId())
                .paymentType(attempt.getPaymentType())
                .paymentInfo(attempt.getPaymentInfo())
                .amount(attempt.getAmount())
                .createdAt(attempt.getCreatedAt())
                .build();

        PaymentCallResult callResult = paymentClient.sendPayment(message);
        return handleResult(message, callResult, attempt);
    }

    private ProcessingResult handleResult(PaymentMessage message,
                                          PaymentCallResult callResult,
                                          PaymentAttempt existingAttempt) {
        Instant now = Instant.now();
        int attemptCount = existingAttempt != null ? existingAttempt.getAttemptCount() + 1 : 1;
        PaymentAttempt attempt = existingAttempt != null ? existingAttempt : PaymentAttempt.builder()
                .id(message.getId())
                .userId(message.getUserId())
                .paymentId(message.getPaymentId())
                .paymentType(message.getPaymentType())
                .paymentInfo(message.getPaymentInfo())
                .amount(message.getAmount())
                .createdAt(message.getCreatedAt())
                .build();

        attempt.setLastAttemptAt(now);
        attempt.setAttemptCount(attemptCount);
        attempt.setLastHttpStatus(callResult.getHttpStatus());
        attempt.setLastError(callResult.getErrorMessage());

        switch (callResult.getStatus()) {
            case SUCCESS -> {
                attempt.setStatus(PaymentAttemptStatus.COMPLETED);
                attempt.setNextAttemptAt(null);
                attemptRepository.save(attempt);
                return ProcessingResult.builder()
                        .resultType(ResultType.SUCCESS)
                        .info("api-success")
                        .build();
            }
            case FATAL_FAILURE -> {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attempt.setNextAttemptAt(null);
                attemptRepository.save(attempt);
                return ProcessingResult.builder()
                        .resultType(ResultType.FATAL)
                        .error(callResult.getErrorMessage())
                        .info("fatal")
                        .build();
            }
            case RETRIABLE_FAILURE -> {
                if (attemptCount >= maxTotalAttempts) {
                    attempt.setStatus(PaymentAttemptStatus.FAILED);
                    attempt.setNextAttemptAt(null);
                    attemptRepository.save(attempt);
                    return ProcessingResult.builder()
                            .resultType(ResultType.FATAL)
                            .error("max-attempts-exceeded: " + callResult.getErrorMessage())
                            .info("fatal-max-attempts")
                            .build();
                }
                Instant next = now.plus(Duration.ofDays(retryIntervalDays))
                        .plusSeconds(randomJitterSeconds());
                attempt.setNextAttemptAt(next);
                attempt.setStatus(PaymentAttemptStatus.PENDING_RETRY);
                attemptRepository.save(attempt);
                return ProcessingResult.builder()
                        .resultType(ResultType.SCHEDULED_RETRY)
                        .info("scheduled-retry")
                        .build();
            }
            default -> {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attemptRepository.save(attempt);
                return ProcessingResult.builder()
                        .resultType(ResultType.FATAL)
                        .error("unknown-result")
                        .build();
            }
        }
    }

    private long randomJitterSeconds() {
        if (jitterSeconds <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(0, jitterSeconds + 1L);
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ProcessingResult {
        private ResultType resultType;
        private String info;
        private String error;
    }

    public enum ResultType {
        SUCCESS,
        SCHEDULED_RETRY,
        FATAL
    }
}

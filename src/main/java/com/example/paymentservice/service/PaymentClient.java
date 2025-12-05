package com.example.paymentservice.service;

import com.example.paymentservice.model.PaymentCallResult;
import com.example.paymentservice.model.PaymentMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);
    private final WebClient paymentWebClient;

    public PaymentCallResult sendPayment(PaymentMessage message) {
        return paymentWebClient.post()
                .uri("/{userId}/payment", message.getUserId())
                .bodyValue(new PaymentRequestPayload(message))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    int statusValue = status.value();
                    HttpStatus resolved = HttpStatus.resolve(statusValue);
                    String reason = resolved != null ? resolved.getReasonPhrase() : status.toString();
                    Mono<String> body = response.bodyToMono(String.class).defaultIfEmpty("");

                    if (status.is2xxSuccessful()) {
                        return body.thenReturn(PaymentCallResult.builder()
                                .status(PaymentCallResult.Status.SUCCESS)
                                .httpStatus(statusValue)
                                .build());
                    }

                    return body.map(errorBody -> {
                        String error = errorBody.isBlank() ? reason : errorBody;
                        PaymentCallResult.Status resultStatus = status.is4xxClientError()
                                ? PaymentCallResult.Status.FATAL_FAILURE
                                : PaymentCallResult.Status.RETRIABLE_FAILURE;
                        log.warn("Payment API {} for paymentId {} (user {}): {}", statusValue, message.getPaymentId(), message.getUserId(), error);
                        return PaymentCallResult.builder()
                                .status(resultStatus)
                                .httpStatus(statusValue)
                                .errorMessage(error)
                                .build();
                    });
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(throwable -> {
                    log.warn("External call error for paymentId {} (user {}): {}", message.getPaymentId(), message.getUserId(), throwable.toString());
                    return Mono.just(PaymentCallResult.builder()
                            .status(PaymentCallResult.Status.RETRIABLE_FAILURE)
                            .errorMessage(throwable.getMessage())
                            .build());
                })
                .block();
    }

    private record PaymentRequestPayload(String paymentId,
                                         String paymentType,
                                         String paymentInfo,
                                         String amount) {
        PaymentRequestPayload(PaymentMessage message) {
            this(message.getPaymentId(),
                    message.getPaymentType().name(),
                    message.getPaymentInfo(),
                    message.getAmount().toPlainString());
        }
    }
}

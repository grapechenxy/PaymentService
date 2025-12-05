package com.example.paymentservice.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "payment_attempts")
public class PaymentAttempt {

    @Id
    private String id;

    @NotNull
    private String userId;

    @NotNull
    private String paymentId;

    @NotNull
    private PaymentType paymentType;

    @NotNull
    private String paymentInfo;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private Instant createdAt;

    private Instant lastAttemptAt;

    private Instant nextAttemptAt;

    private int attemptCount;

    @NotNull
    private PaymentAttemptStatus status;

    private String lastError;

    private Integer lastHttpStatus;
}

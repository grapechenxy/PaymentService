package com.example.paymentservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMessage {

    @NotBlank
    private String id;

    @NotBlank
    private String userId;

    @NotBlank
    private String paymentId;

    @NotNull
    private PaymentType paymentType;

    @NotBlank
    private String paymentInfo;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    private String correlationId;
}

package com.example.paymentservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCallResult {
    public enum Status {
        SUCCESS,
        RETRIABLE_FAILURE,
        FATAL_FAILURE
    }

    private Status status;
    private String errorMessage;
    private Integer httpStatus;
}

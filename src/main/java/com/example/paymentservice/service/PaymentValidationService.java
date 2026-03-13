package com.example.paymentservice.service;

import com.example.paymentservice.model.PaymentMessage;
import com.example.paymentservice.model.PaymentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PaymentValidationService {

    private final Validator validator;

    private static final Pattern SWISH_PATTERN = Pattern.compile("^\\+\\d{8,15}$");
    private static final Pattern BANK_PATTERN = Pattern.compile("^[A-Za-z0-9]{10,34}$");
    private static final Pattern PG_PATTERN = Pattern.compile("^\\d{2,8}(-\\d{2,4})?$");
    private static final Pattern BG_PATTERN = Pattern.compile("^\\d{2,8}(-\\d{1,4})?$");

    public List<String> validate(PaymentMessage message) {
        List<String> errors = new ArrayList<>();

        Set<ConstraintViolation<PaymentMessage>> violations = validator.validate(message);
        violations.forEach(v -> errors.add(v.getPropertyPath() + " " + v.getMessage()));

        if (message.getId() != null) {
            try {
                UUID.fromString(message.getId());
            } catch (IllegalArgumentException e) {
                errors.add("id must be a valid UUID");
            }
        }

        PaymentType type = message.getPaymentType();
        String info = message.getPaymentInfo();
        if (type != null && info != null) {
            switch (type) {
                case swish -> {
                    if (!SWISH_PATTERN.matcher(info).matches()) {
                        errors.add("paymentInfo invalid swish format");
                    }
                }
                case bankaccount -> {
                    if (!BANK_PATTERN.matcher(info).matches()) {
                        errors.add("paymentInfo invalid bankaccount format");
                    }
                }
                case pg -> {
                    if (!PG_PATTERN.matcher(info).matches()) {
                        errors.add("paymentInfo invalid pg format");
                    }
                }
                case bg -> {
                    if (!BG_PATTERN.matcher(info).matches()) {
                        errors.add("paymentInfo invalid bg format");
                    }
                }
                default -> {
                }
            }
        }

        return errors;
    }
}

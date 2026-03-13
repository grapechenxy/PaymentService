package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentAttempt;
import com.example.paymentservice.model.PaymentAttemptStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface PaymentAttemptRepository extends MongoRepository<PaymentAttempt, String> {
    List<PaymentAttempt> findByStatusAndNextAttemptAtBefore(PaymentAttemptStatus status, Instant threshold);
}

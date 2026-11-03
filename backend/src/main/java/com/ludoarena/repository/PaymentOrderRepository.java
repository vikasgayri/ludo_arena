package com.ludoarena.repository;

import com.ludoarena.model.PaymentOrder;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentOrderRepository extends MongoRepository<PaymentOrder, String> {
    Optional<PaymentOrder> findByProviderOrderId(String providerOrderId);
}

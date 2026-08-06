package com.example.smartmanager.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByTransactionId(String transactionId);
    List<PaymentOrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

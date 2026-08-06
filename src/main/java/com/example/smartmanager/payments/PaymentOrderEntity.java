package com.example.smartmanager.payments;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_type", nullable = false)
    private String planType; // PRO, ENTERPRISE

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle; // monthly, annual

    @Column(nullable = false)
    private Long amount; // Amount in VND

    @Column(nullable = false)
    private String currency = "VND";

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod; // VNPAY, MOMO, PAYPAL, CREDIT_CARD

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED, FAILED, CANCELLED

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "qr_code_url", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "voucher_code")
    private String voucherCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

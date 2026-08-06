package com.example.smartmanager.payments;

import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public PaymentOrderEntity createOrder(
            String userId,
            String planType,
            String billingCycle,
            String paymentMethod,
            String voucherCode
    ) {
        UUID uId = UUID.fromString(userId);

        // Price calculation
        long basePrice = "ENTERPRISE".equalsIgnoreCase(planType)
                ? ("annual".equalsIgnoreCase(billingCycle) ? 399000L : 499000L)
                : ("annual".equalsIgnoreCase(billingCycle) ? 159000L : 199000L);

        double discount = 0.0;
        if ("HOMIX2026".equalsIgnoreCase(voucherCode)) {
            discount = 0.20; // 20% discount
        }

        long finalAmount = Math.max(0L, (long) (basePrice * (1.0 - discount)));
        String txnRef = "HOMIX_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);

        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setUserId(uId);
        order.setPlanType(planType.toUpperCase());
        order.setBillingCycle(billingCycle.toLowerCase());
        order.setAmount(finalAmount);
        order.setCurrency("VND");
        order.setPaymentMethod(paymentMethod.toUpperCase());
        order.setStatus("PENDING");
        order.setTransactionId(txnRef);
        order.setVoucherCode(voucherCode);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Build URLs based on payment method
        String encodedTxn = URLEncoder.encode(txnRef, StandardCharsets.UTF_8);
        String qrData = "HOMIX_PAYMENT_" + txnRef + "_" + finalAmount;

        if ("VNPAY".equalsIgnoreCase(paymentMethod)) {
            order.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=" + (finalAmount * 100) + "&vnp_Command=pay&vnp_CreateDate=20260806120000&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=" + encodedTxn + "&vnp_OrderType=other&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A3000%2Fcheckout%3Fstatus%3Dsuccess&vnp_TmnCode=HOMIXVN1&vnp_TxnRef=" + encodedTxn + "&vnp_Version=2.1.0&vnp_SecureHash=mock_hash");
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VNPAY:" + qrData, StandardCharsets.UTF_8));
        } else if ("MOMO".equalsIgnoreCase(paymentMethod)) {
            order.setPaymentUrl("https://test-payment.momo.vn/v2/gateway/api/create?partnerCode=MOMO&orderId=" + encodedTxn + "&amount=" + finalAmount);
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("MOMO:" + qrData, StandardCharsets.UTF_8));
        } else if ("PAYPAL".equalsIgnoreCase(paymentMethod)) {
            order.setPaymentUrl("https://www.sandbox.paypal.com/checkoutnow?token=" + txnRef);
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("PAYPAL:" + qrData, StandardCharsets.UTF_8));
        } else {
            // CREDIT_CARD or Direct Bank VietQR
            order.setPaymentUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VIETQR:" + qrData, StandardCharsets.UTF_8));
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VIETQR:" + qrData, StandardCharsets.UTF_8));
        }

        return paymentOrderRepository.save(order);
    }

    @Transactional
    public PaymentOrderEntity confirmAndCompleteOrder(String orderIdOrTxnId) {
        PaymentOrderEntity order = null;
        try {
            UUID id = UUID.fromString(orderIdOrTxnId);
            order = paymentOrderRepository.findById(id).orElse(null);
        } catch (Exception e) {
            order = paymentOrderRepository.findByTransactionId(orderIdOrTxnId).orElse(null);
        }

        if (order == null) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng thanh toán: " + orderIdOrTxnId);
        }

        if ("COMPLETED".equals(order.getStatus())) {
            return order;
        }

        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        PaymentOrderEntity savedOrder = paymentOrderRepository.save(order);

        // Update User Subscription Plan
        Optional<UserEntity> userOpt = userRepository.findById(savedOrder.getUserId());
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            user.setSubscriptionPlan(savedOrder.getPlanType());
            int days = "annual".equalsIgnoreCase(savedOrder.getBillingCycle()) ? 365 : 30;
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(days));
            userRepository.save(user);

            // Send notification
            try {
                notificationService.createNotification(
                        user.getId().toString(),
                        "Nâng cấp gói dịch vụ thành công!",
                        "Chúc mừng! Bạn đã nâng cấp thành công gói dịch vụ Homix v2.0 " + savedOrder.getPlanType() + " (" + savedOrder.getBillingCycle() + ").",
                        "SYSTEM"
                );
            } catch (Exception e) {
                System.err.println("Failed to send subscription upgrade notification: " + e.getMessage());
            }
        }

        return savedOrder;
    }

    public PaymentOrderEntity getOrderById(String orderId) {
        return paymentOrderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại: " + orderId));
    }

    public List<PaymentOrderEntity> getUserOrders(String userId) {
        return paymentOrderRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
    }
}

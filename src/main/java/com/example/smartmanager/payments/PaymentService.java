package com.example.smartmanager.payments;

import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${PAYPAL_CLIENT_ID:AULIBK_ava0E1QxLYbRUHI-PkmzzAtCkgKUfBa8O-6MRh2ukhB_Rp4n6Zbl86cXNATk-p6pvC2POzZ7Y}")
    private String paypalClientId;

    @Value("${PAYPAL_APP_NAME:Homix}")
    private String paypalAppName;

    @Value("${VNPAY_TMN_CODE:NS4R2SPO}")
    private String vnpTmnCode;

    @Value("${VNPAY_HASH_SECRET:EPRTWZDPHRVEQOMRWWFZEKQRGYDRCUIO}")
    private String vnpHashSecret;

    @Value("${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpPayUrl;

    @Value("${VNPAY_RETURN_URL:http://localhost:3000/checkout?status=success}")
    private String vnpReturnUrl;

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
            String vnpUrl = buildVnPayUrl(txnRef, finalAmount);
            order.setPaymentUrl(vnpUrl);
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VNPAY:" + vnpUrl, StandardCharsets.UTF_8));
        } else if ("MOMO".equalsIgnoreCase(paymentMethod)) {
            order.setPaymentUrl("https://test-payment.momo.vn/v2/gateway/api/create?partnerCode=MOMO&orderId=" + encodedTxn + "&amount=" + finalAmount);
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("MOMO:" + qrData, StandardCharsets.UTF_8));
        } else if ("PAYPAL".equalsIgnoreCase(paymentMethod)) {
            double usdAmount = "ENTERPRISE".equalsIgnoreCase(planType) ? 19.99 : 7.99;
            String itemName = URLEncoder.encode("Homix " + planType + " VIP Subscription (" + billingCycle + ")", StandardCharsets.UTF_8);
            order.setPaymentUrl("https://www.sandbox.paypal.com/cgi-bin/webscr?cmd=_xclick&business=" + paypalClientId + "&item_name=" + itemName + "&amount=" + String.format(java.util.Locale.US, "%.2f", usdAmount) + "&currency_code=USD&custom=" + encodedTxn + "&return=http%3A%2F%2Flocalhost%3A3000%2Fcheckout%3Fstatus%3Dsuccess");
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("PAYPAL_APP:" + paypalAppName + "|CLIENT_ID:" + paypalClientId + "|ORDER:" + txnRef, StandardCharsets.UTF_8));
        } else {
            // CREDIT_CARD or Direct Bank VietQR
            order.setPaymentUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VIETQR:" + qrData, StandardCharsets.UTF_8));
            order.setQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + URLEncoder.encode("VIETQR:" + qrData, StandardCharsets.UTF_8));
        }

        return paymentOrderRepository.save(order);
    }

    private String buildVnPayUrl(String txnRef, long amount) {
        try {
            Map<String, String> vnpParams = new HashMap<>();
            vnpParams.put("vnp_Version", "2.1.0");
            vnpParams.put("vnp_Command", "pay");
            vnpParams.put("vnp_TmnCode", vnpTmnCode);
            vnpParams.put("vnp_Amount", String.valueOf(amount * 100));
            vnpParams.put("vnp_CurrCode", "VND");
            vnpParams.put("vnp_TxnRef", txnRef);
            vnpParams.put("vnp_OrderInfo", "Thanh toan don hang Homix " + txnRef);
            vnpParams.put("vnp_OrderType", "other");
            vnpParams.put("vnp_Locale", "vn");
            vnpParams.put("vnp_ReturnUrl", vnpReturnUrl);
            vnpParams.put("vnp_IpAddr", "127.0.0.1");

            Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String vnpCreateDate = formatter.format(cld.getTime());
            vnpParams.put("vnp_CreateDate", vnpCreateDate);

            cld.add(Calendar.MINUTE, 15);
            String vnpExpireDate = formatter.format(cld.getTime());
            vnpParams.put("vnp_ExpireDate", vnpExpireDate);

            List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
            Collections.sort(fieldNames);

            StringBuilder hashData = new StringBuilder();
            StringBuilder query = new StringBuilder();

            Iterator<String> itr = fieldNames.iterator();
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = vnpParams.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }

            String queryUrl = query.toString();
            String vnpSecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
            queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

            return vnpPayUrl + "?" + queryUrl;
        } catch (Exception e) {
            return vnpPayUrl;
        }
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
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

    @Transactional
    public void cancelSubscription(String userId) {
        UUID uId = UUID.fromString(userId);
        UserEntity user = userRepository.findById(uId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin người dùng"));

        user.setSubscriptionPlan("FREE");
        user.setSubscriptionExpiresAt(null);
        userRepository.save(user);

        try {
            notificationService.createNotification(
                    user.getId().toString(),
                    "Đã hủy gói dịch vụ thành công",
                    "Bạn đã hủy gói dịch vụ trả phí thành công. Tài khoản của bạn đã chuyển về Gói Miễn Phí (FREE). Bạn có thể đổi gói bất kỳ lúc nào.",
                    "SYSTEM"
            );
        } catch (Exception e) {
            System.err.println("Failed to send subscription cancellation notification: " + e.getMessage());
        }
    }
}

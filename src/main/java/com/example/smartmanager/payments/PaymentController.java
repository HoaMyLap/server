package com.example.smartmanager.payments;

import com.example.smartmanager.auth.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request
    ) {
        String userId = userPrincipal != null ? userPrincipal.getId().toString() : request.get("userId");
        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Vui lòng đăng nhập để thực hiện thanh toán đơn hàng."));
        }

        String planType = request.getOrDefault("planType", "PRO");
        String billingCycle = request.getOrDefault("billingCycle", "annual");
        String paymentMethod = request.getOrDefault("paymentMethod", "VNPAY");
        String voucherCode = request.get("voucherCode");

        PaymentOrderEntity order = paymentService.createOrder(
                userId,
                planType,
                billingCycle,
                paymentMethod,
                voucherCode
        );

        return ResponseEntity.ok(order);
    }

    @PostMapping("/confirm-payment")
    public ResponseEntity<PaymentOrderEntity> confirmPayment(
            @RequestBody Map<String, String> request
    ) {
        String orderId = request.get("orderId");
        if (orderId == null || orderId.trim().isEmpty()) {
            orderId = request.get("transactionId");
        }

        PaymentOrderEntity completed = paymentService.confirmAndCompleteOrder(orderId);
        return ResponseEntity.ok(completed);
    }

    @GetMapping("/order-status/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@PathVariable("orderId") String orderId) {
        PaymentOrderEntity order = paymentService.getOrderById(orderId);
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getId());
        response.put("transactionId", order.getTransactionId());
        response.put("status", order.getStatus());
        response.put("planType", order.getPlanType());
        response.put("amount", order.getAmount());
        response.put("paymentMethod", order.getPaymentMethod());
        response.put("updatedAt", order.getUpdatedAt());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user-orders")
    public ResponseEntity<?> getUserOrders(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (userPrincipal == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(paymentService.getUserOrders(userPrincipal.getId().toString()));
    }

    // =========================================================================
    // GATEWAY CALLBACKS & WEBHOOK IPN ENDPOINTS
    // =========================================================================

    @GetMapping("/vnpay/callback")
    public ResponseEntity<Map<String, String>> vnpayCallback(
            @RequestParam Map<String, String> allParams
    ) {
        String txnRef = allParams.get("vnp_TxnRef");
        String responseCode = allParams.get("vnp_ResponseCode");

        Map<String, String> res = new HashMap<>();
        if ("00".equals(responseCode) && txnRef != null) {
            paymentService.confirmAndCompleteOrder(txnRef);
            res.put("status", "SUCCESS");
            res.put("message", "Giao dịch VNPay thành công");
        } else {
            res.put("status", "FAILED");
            res.put("message", "Giao dịch VNPay thất bại hoặc bị hủy");
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/momo/ipn")
    public ResponseEntity<Map<String, String>> momoIpn(
            @RequestBody Map<String, Object> body
    ) {
        String orderId = (String) body.get("orderId");
        Integer resultCode = (Integer) body.get("resultCode");

        Map<String, String> res = new HashMap<>();
        if (resultCode != null && resultCode == 0 && orderId != null) {
            paymentService.confirmAndCompleteOrder(orderId);
            res.put("status", "SUCCESS");
        } else {
            res.put("status", "FAILED");
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/paypal/capture")
    public ResponseEntity<Map<String, String>> paypalCapture(
            @RequestBody Map<String, String> body
    ) {
        String orderId = body.get("orderId");
        String txnId = body.get("transactionId");
        String ref = orderId != null ? orderId : txnId;

        paymentService.confirmAndCompleteOrder(ref);

        Map<String, String> res = new HashMap<>();
        res.put("status", "SUCCESS");
        res.put("message", "Giao dịch PayPal hoàn tất thành công");
        return ResponseEntity.ok(res);
    }
}

package com.localcloud.paymentservice.payment;

import com.localcloud.paymentservice.payment.event.OrderCreatedEvent;
import com.localcloud.paymentservice.payment.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public PaymentCompletedEvent processPayment(OrderCreatedEvent event) {
        // 지금 단계에서는 실제 결제 API를 호출하지 않고, 이벤트 소비 흐름만 검증합니다.
        log.info(
                "Payment completed. orderId={}, userId={}, productId={}, quantity={}",
                event.orderId(),
                event.userId(),
                event.productId(),
                event.quantity()
        );

        return PaymentCompletedEvent.from(event);
    }
}

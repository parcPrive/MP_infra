package com.localcloud.paymentservice.payment.event;

import com.localcloud.paymentservice.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public PaymentEventConsumer(ObjectMapper objectMapper, PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.order-created:order.created}",
            groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
            autoStartup = "${app.kafka.listener.enabled:true}"
    )
    public void consumeOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

            log.info("Consumed order.created event. orderId={}", event.orderId());
            paymentService.processPayment(event);
        } catch (JacksonException exception) {
            // 다음 단계에서 DLQ를 붙이기 전까지는 파싱 실패 메시지를 로그로 남깁니다.
            log.warn("Failed to parse order.created event. message={}", message, exception);
        }
    }
}

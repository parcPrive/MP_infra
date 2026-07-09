package com.localcloud.paymentservice.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String paymentCompletedTopic;

    public PaymentEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.payment-completed:payment.completed}") String paymentCompletedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentCompletedTopic = paymentCompletedTopic;
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);

            // key를 orderId로 맞춰두면 같은 주문의 후속 이벤트가 같은 기준으로 분산됩니다.
            kafkaTemplate.send(paymentCompletedTopic, event.orderId().toString(), eventJson)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to publish payment.completed event. orderId={}", event.orderId(), exception);
                            return;
                        }

                        log.info(
                                "Published payment.completed event. orderId={}, topic={}, partition={}, offset={}",
                                event.orderId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    });
        } catch (JacksonException exception) {
            log.warn("Failed to serialize payment.completed event. orderId={}", event.orderId(), exception);
        } catch (RuntimeException exception) {
            log.warn("Kafka publish request failed before send. orderId={}", event.orderId(), exception);
        }
    }
}

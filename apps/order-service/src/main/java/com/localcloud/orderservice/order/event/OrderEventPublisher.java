package com.localcloud.orderservice.order.event;

import com.localcloud.orderservice.order.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String orderCreatedTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic.order-created:order.created}") String orderCreatedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    public void publishOrderCreated(OrderResponse order) {
        OrderCreatedEvent event = OrderCreatedEvent.from(order);

        try {
            String eventJson = objectMapper.writeValueAsString(event);

            // key를 orderId로 주면 같은 주문 이벤트는 같은 파티션으로 가게 됩니다.
            kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), eventJson)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to publish order.created event. orderId={}", event.orderId(), exception);
                            return;
                        }

                        log.info(
                                "Published order.created event. orderId={}, topic={}, partition={}, offset={}",
                                event.orderId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    });
        } catch (JacksonException exception) {
            log.warn("Failed to serialize order.created event. orderId={}", event.orderId(), exception);
        } catch (RuntimeException exception) {
            log.warn("Kafka publish request failed before send. orderId={}", event.orderId(), exception);
        }
    }
}

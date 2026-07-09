package com.localcloud.inventoryservice.inventory.event;

import com.localcloud.inventoryservice.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    public InventoryEventConsumer(ObjectMapper objectMapper, InventoryService inventoryService) {
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.payment-completed:payment.completed}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}",
            autoStartup = "${app.kafka.listener.enabled:true}"
    )
    public void consumePaymentCompleted(String message) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);

            log.info("Consumed payment.completed event. orderId={}", event.orderId());
            inventoryService.decreaseInventory(event);
        } catch (JacksonException exception) {
            log.warn("Failed to parse payment.completed event. message={}", message, exception);
            throw new IllegalArgumentException("Invalid payment.completed event payload", exception);
        }
    }
}

package com.localcloud.inventoryservice.inventory;

import com.localcloud.inventoryservice.inventory.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public void decreaseInventory(PaymentCompletedEvent event) {
        // 지금 단계에서는 실제 재고 DB 없이 이벤트 소비 흐름을 검증합니다.
        log.info(
                "Inventory decreased. orderId={}, productId={}, quantity={}, paymentId={}",
                event.orderId(),
                event.productId(),
                event.quantity(),
                event.paymentId()
        );
    }
}

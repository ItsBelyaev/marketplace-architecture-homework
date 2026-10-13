package ru.itsbelyaev.marketplace.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.generated.api.OrdersApiDelegate;
import ru.itsbelyaev.marketplace.generated.model.OrderCreate;
import ru.itsbelyaev.marketplace.generated.model.OrderResponse;
import ru.itsbelyaev.marketplace.generated.model.OrderUpdate;
import ru.itsbelyaev.marketplace.service.OrderService;

@Service
public class OrdersApiDelegateImpl implements OrdersApiDelegate {
    private final OrderService orderService;

    public OrdersApiDelegateImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ResponseEntity<OrderResponse> createOrder(OrderCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(UUID id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @Override
    public ResponseEntity<OrderResponse> updateOrder(UUID id, OrderUpdate request) {
        return ResponseEntity.ok(orderService.update(id, request));
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UUID id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}

package ru.itsbelyaev.marketplace.api;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.generated.api.HealthApiDelegate;
import ru.itsbelyaev.marketplace.generated.model.HealthResponse;

@Service
public class HealthApiDelegateImpl implements HealthApiDelegate {
    @Override
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ok"));
    }
}

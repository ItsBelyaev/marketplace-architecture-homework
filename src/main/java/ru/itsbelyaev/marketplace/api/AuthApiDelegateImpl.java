package ru.itsbelyaev.marketplace.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.generated.api.AuthApiDelegate;
import ru.itsbelyaev.marketplace.generated.model.AuthTokens;
import ru.itsbelyaev.marketplace.generated.model.LoginRequest;
import ru.itsbelyaev.marketplace.generated.model.RefreshRequest;
import ru.itsbelyaev.marketplace.generated.model.RegisterRequest;
import ru.itsbelyaev.marketplace.service.AuthService;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {
    private final AuthService authService;

    public AuthApiDelegateImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<AuthTokens> register(RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Override
    public ResponseEntity<AuthTokens> login(LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Override
    public ResponseEntity<AuthTokens> refresh(RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}

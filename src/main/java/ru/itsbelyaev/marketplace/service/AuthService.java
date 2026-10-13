package ru.itsbelyaev.marketplace.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.domain.UserRow;
import ru.itsbelyaev.marketplace.error.BusinessException;
import ru.itsbelyaev.marketplace.generated.model.AuthTokens;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;
import ru.itsbelyaev.marketplace.generated.model.LoginRequest;
import ru.itsbelyaev.marketplace.generated.model.RefreshRequest;
import ru.itsbelyaev.marketplace.generated.model.RegisterRequest;
import ru.itsbelyaev.marketplace.repository.UserRepository;
import ru.itsbelyaev.marketplace.security.JwtService;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthTokens register(RegisterRequest request) {
        if (users.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Username already exists", Map.of("field", "username"));
        }
        UserRow user = users.insert(request.getUsername(), passwordEncoder.encode(request.getPassword()),
                request.getRole().getValue());
        return jwtService.issue(user.id(), user.username(), user.role());
    }

    public AuthTokens login(LoginRequest request) {
        UserRow user = users.findByUsername(request.getUsername())
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.getPassword(), user.passwordHash())) {
            throw invalidCredentials();
        }
        return jwtService.issue(user.id(), user.username(), user.role());
    }

    public AuthTokens refresh(RefreshRequest request) {
        try {
            DecodedJWT jwt = jwtService.verifyRefresh(request.getRefreshToken());
            UUID userId = UUID.fromString(jwt.getSubject());
            UserRow user = users.findById(userId).orElseThrow();
            return jwtService.issue(user.id(), user.username(), user.role());
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_TOKEN_INVALID,
                    "Refresh token is invalid");
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID,
                "Invalid username or password");
    }
}

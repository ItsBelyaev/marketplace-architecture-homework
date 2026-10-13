package ru.itsbelyaev.marketplace.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.config.AppProperties;
import ru.itsbelyaev.marketplace.generated.model.AuthTokens;

@Service
public class JwtService {
    private final AppProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.jwtSecret());
        this.verifier = JWT.require(algorithm).withIssuer("marketplace").build();
    }

    public AuthTokens issue(UUID userId, String username, String role) {
        Instant now = Instant.now();
        String access = create(userId, username, role, "access",
                now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES));
        String refresh = create(userId, username, role, "refresh",
                now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS));
        return new AuthTokens(access, refresh, "Bearer");
    }

    public DecodedJWT verifyAccess(String token) {
        DecodedJWT jwt = verifier.verify(token);
        if (!"access".equals(jwt.getClaim("type").asString())) {
            throw new com.auth0.jwt.exceptions.JWTVerificationException("Not an access token");
        }
        return jwt;
    }

    public DecodedJWT verifyRefresh(String token) {
        DecodedJWT jwt = verifier.verify(token);
        if (!"refresh".equals(jwt.getClaim("type").asString())) {
            throw new com.auth0.jwt.exceptions.JWTVerificationException("Not a refresh token");
        }
        return jwt;
    }

    private String create(UUID userId, String username, String role, String type, Instant expiresAt) {
        return JWT.create()
                .withIssuer("marketplace")
                .withSubject(userId.toString())
                .withClaim("username", username)
                .withClaim("role", role)
                .withClaim("type", type)
                .withIssuedAt(Instant.now())
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }
}

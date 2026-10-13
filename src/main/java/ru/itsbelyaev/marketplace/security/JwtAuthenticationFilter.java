package ru.itsbelyaev.marketplace.security;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itsbelyaev.marketplace.error.SecurityErrorWriter;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            DecodedJWT jwt = jwtService.verifyAccess(header.substring(7));
            String role = jwt.getClaim("role").asString();
            AuthenticatedUser user = new AuthenticatedUser(
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaim("username").asString(),
                    role
            );
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute("user_id", user.id().toString());
            chain.doFilter(request, response);
        } catch (TokenExpiredException exception) {
            errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), ErrorCode.TOKEN_EXPIRED,
                    "Access token expired");
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), ErrorCode.TOKEN_INVALID,
                    "Access token is invalid");
        }
    }
}

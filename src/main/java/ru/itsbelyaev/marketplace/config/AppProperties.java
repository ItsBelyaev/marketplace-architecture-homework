package ru.itsbelyaev.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        long orderLimitMinutes
) {
}

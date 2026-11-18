package com.psy.trainer.security;

import com.psy.trainer.config.PsyProperties;
import com.psy.trainer.domain.AppRole;
import com.psy.trainer.domain.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final PsyProperties properties;

    public JwtService(JwtEncoder jwtEncoder, PsyProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        long ttlSeconds = Math.max(300, properties.getJwt().getTtlSeconds());
        Instant exp = now.plusSeconds(ttlSeconds);
        List<String> roles = user.roleSet().stream().map(AppRole::name).sorted().toList();

        var claimsBuilder = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(now)
                .expiresAt(exp)
                .subject(user.getId().toString())
                .claim("preferred_username", user.getUsername())
                .claim("name", user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName()
                        : user.getUsername())
                .claim("roles", roles);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            claimsBuilder.claim("email", user.getEmail());
        }
        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttlSeconds, exp);
    }

    public record IssuedToken(String accessToken, long expiresInSeconds, Instant expiresAt) {
    }
}

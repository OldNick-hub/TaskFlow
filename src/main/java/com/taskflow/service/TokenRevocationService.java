package com.taskflow.service;

import com.taskflow.model.RevokedToken;
import com.taskflow.repository.RevokedTokenRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Manages persistent JWT revocation records backed by MongoDB with TTL cleanup.
 */
@Service
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenRevocationService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void revokeToken(JwtService.JwtClaims claims) {
        if (revokedTokenRepository.existsByTokenId(claims.tokenId())) {
            return;
        }
        RevokedToken revokedToken = new RevokedToken(
                claims.tokenId(),
                claims.userId(),
                claims.expiresAt(),
                Instant.now());
        try {
            revokedTokenRepository.save(revokedToken);
        } catch (DuplicateKeyException ignored) {
            // Token was already revoked concurrently; treat logout as idempotent.
        }
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && revokedTokenRepository.existsByTokenId(tokenId);
    }
}

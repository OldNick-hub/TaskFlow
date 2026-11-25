package com.taskflow.repository;

import com.taskflow.model.RevokedToken;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link RevokedToken} documents.
 */
public interface RevokedTokenRepository extends MongoRepository<RevokedToken, String> {

    boolean existsByTokenId(String tokenId);
}

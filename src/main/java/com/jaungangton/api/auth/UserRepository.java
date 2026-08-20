package com.jaungangton.api.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByNormalizedNicknameAndIdNot(String normalizedNickname, UUID id);
}

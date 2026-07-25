package com.example.smartmanager.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, java.util.UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
}

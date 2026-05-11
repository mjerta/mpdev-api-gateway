package com.example.gateway_service.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.gateway_service.models.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByExternalId(String externalId);

  Optional<User> findByEmail(String email);
}

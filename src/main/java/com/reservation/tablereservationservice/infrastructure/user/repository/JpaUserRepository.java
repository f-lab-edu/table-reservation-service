package com.reservation.tablereservationservice.infrastructure.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {
}

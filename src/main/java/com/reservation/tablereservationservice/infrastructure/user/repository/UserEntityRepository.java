package com.reservation.tablereservationservice.infrastructure.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;

interface UserEntityRepository extends JpaRepository<UserEntity, Long> {

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);
}
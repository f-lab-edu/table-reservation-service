package com.reservation.tablereservationservice.infrastructure.user.repository;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

	private final JpaUserRepository jpaUserRepository;

}

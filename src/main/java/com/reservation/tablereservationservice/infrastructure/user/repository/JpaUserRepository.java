package com.reservation.tablereservationservice.infrastructure.user.repository;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;
import com.reservation.tablereservationservice.infrastructure.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

	private final UserEntityRepository userEntityRepository;

	@Override
	public User save(User user) {

		UserEntity userEntity = UserMapper.INSTANCE.toEntity(user);
		UserEntity savedUserEntity =  userEntityRepository.save(userEntity);

		return UserMapper.INSTANCE.toDomain(savedUserEntity);

	}

	@Override
	public boolean existsByEmail(String email) {
		return userEntityRepository.existsByEmail(email);
	}

	@Override
	public boolean existsByPhone(String phone) {
		return userEntityRepository.existsByPhone(phone);
	}

}

package com.reservation.tablereservationservice.infrastructure.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.infrastructure.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

	private final UserEntityRepository userEntityRepository;

	@Override
	public User save(User user) {

		// UserEntity userEntity = UserEntity.from(user);
		// UserEntity savedUserEntity =  userEntityRepository.save(userEntity);
		//
 		// infra 레이어에서 도메인 -> 엔티티, 엔티티 -> 도메인 바꾸는 과정 필요
		// return savedUserEntity.toDomain();

		return null;
	}

	interface UserEntityRepository extends JpaRepository<UserEntity, Long> {

	}
}

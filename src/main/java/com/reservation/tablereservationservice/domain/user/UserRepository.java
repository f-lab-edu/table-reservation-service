package com.reservation.tablereservationservice.domain.user;

public interface UserRepository {

	User save(User user);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);
}

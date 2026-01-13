package com.reservation.tablereservationservice.domain.user;

import java.util.Optional;

public interface UserRepository {

	User save(User user);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	Optional<User> findByEmail(String email);
}

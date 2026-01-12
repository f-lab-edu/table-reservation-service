package com.reservation.tablereservationservice.domain.user;

public interface UserRepository {

	// 여기에 추후 infra crud에서 활용될 인터페이스 명세가 들어감
	User save(User user);
}

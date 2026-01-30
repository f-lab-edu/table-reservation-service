package com.reservation.tablereservationservice.fixture;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRole;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFixture {

	private Long userId = 1L;
	private String email = "customer@test.com";
	private String name = "user";
	private String phone = "010-0000-0000";
	private String password = "encrypted-password";
	private UserRole userRole = UserRole.CUSTOMER;

	public static UserFixture customer() {
		return new UserFixture()
			.userRole(UserRole.CUSTOMER)
			.email("customer@test.com")
			.name("customer")
			.phone("010-0000-0000");
	}

	public static UserFixture owner() {
		return new UserFixture()
			.userRole(UserRole.OWNER)
			.email("owner@test.com")
			.name("owner")
			.phone("010-0000-0001");
	}

	public User build() {
		return User.builder()
			.userId(userId)
			.email(email)
			.name(name)
			.phone(phone)
			.password(password)
			.userRole(userRole)
			.build();
	}
}

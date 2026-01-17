package com.reservation.tablereservationservice.domain.user;

import lombok.Builder;
import lombok.Getter;

@Getter
public class User {

	private Long userId;
	private String email;
	private String password;
	private String name;
	private String phone;
	private UserRole userRole;

	@Builder
	public User(Long userId, String email, String password, String name, String phone, UserRole userRole) {
		this.userId = userId;
		this.email = email;
		this.password = password;
		this.name = name;
		this.phone = phone;
		this.userRole = userRole;
	}

	public void encryptPassword(String encryptPassword) {
		this.password = encryptPassword;
	}
}

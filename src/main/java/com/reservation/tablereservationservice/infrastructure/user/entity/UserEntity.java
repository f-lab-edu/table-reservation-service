package com.reservation.tablereservationservice.infrastructure.user.entity;

import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "user_email", length = 100, nullable = false, unique = true)
	private String email;

	@Column(name = "user_password", nullable = false)
	private String password;

	@Column(name = "user_name", length = 50, nullable = false)
	private String name;

	@Column(name = "user_phone", length = 20, nullable = false, unique = true)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_role", length = 20, nullable = false)
	private UserRole userRole;

	@Builder
	private UserEntity(String email, String password, String name, String phone, UserRole userRole) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.phone = phone;
		this.userRole = userRole;
	}

}

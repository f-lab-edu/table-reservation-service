package com.reservation.tablereservationservice.presentation.user.dto;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignUpRequestDto {

	@NotBlank(message = "이메일은 필수 입력 값입니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	private String email;

	@NotBlank(message = "비밀번호는 필수 입력 값입니다.")
	@Pattern(
		regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&]).{8,20}$",
		message = "비밀번호는 8~20자의 영문 대소문자, 숫자, 특수문자를 포함해야 합니다.")
	private String password;

	@NotBlank(message = "이름은 필수 입력 값입니다.")
	@Size(max = 8, message = "이름은 8자 이하로 입력해야 합니다.")
	private String name;

	@NotBlank(message = "전화번호는 필수 입력 값입니다.")
	@Pattern(
		regexp = "^01(?:0|1|[6-9])-(?:\\d{3}|\\d{4})-\\d{4}$",
		message = "전화번호 형식(010-0000-0000)이 올바르지 않습니다.")
	private String phone;

	@NotBlank(message = "유저 역할은 필수 입력 값입니다.")
	@Pattern(regexp = "^(CUSTOMER|OWNER)$", message = "userRole은 CUSTOMER 또는 OWNER여야 합니다.")
	private String userRole;

	public User toDomain() {
		return User.builder()
			.email(this.email)
			.password(this.password)
			.name(this.name)
			.phone(this.phone)
			.userRole(UserRole.valueOf(this.userRole))
			.build();
	}
}

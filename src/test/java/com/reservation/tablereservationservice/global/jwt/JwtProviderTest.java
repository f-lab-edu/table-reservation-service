package com.reservation.tablereservationservice.global.jwt;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import com.reservation.tablereservationservice.domain.user.UserRole;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtProviderTest {

	private static final String TEST_BASE64_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Test
	@DisplayName("토큰 생성 및 인증 객체 추출 성공")
	void createAccessToken_And_GetAuthentication_Success() {
		// given
		JwtProvider jwtProvider = new JwtProvider(TEST_BASE64_SECRET, 60_000L);
		String email = "tester@email.com";
		UserRole role = UserRole.CUSTOMER;

		// when
		String token = jwtProvider.createAccessToken(email, role);
		Authentication authentication = jwtProvider.getAuthenticationFromAccessToken(token);

		// then
		assertThat(token).isNotBlank();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(email);
		assertThat(authentication.getAuthorities())
			.extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_CUSTOMER");
	}

	@Test
	@DisplayName("만료된 토큰이면 ExpiredJwtException이 발생한다")
	void getAuthentication_ExpiredToken_ThrowsExpiredJwtException() {
		// given
		// expirationMs를 음수로 만들어 즉시 만료 토큰 생성
		JwtProvider jwtProvider = new JwtProvider(TEST_BASE64_SECRET, -1L);
		String token = jwtProvider.createAccessToken("tester@email.com", UserRole.CUSTOMER);

		// when & then
		assertThatThrownBy(() -> jwtProvider.getAuthenticationFromAccessToken(token))
			.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	@DisplayName("위조(변조) 토큰이면 JwtException이 발생한다")
	void getAuthentication_TamperedToken_ThrowsJwtException() {
		// given
		JwtProvider jwtProvider = new JwtProvider(TEST_BASE64_SECRET, 60_000L);
		String token = jwtProvider.createAccessToken("tester@email.com", UserRole.CUSTOMER);

		// 토큰 일부 변조
		String tampered = token.substring(0, token.length() - 2) + "xx";

		// when & then
		assertThatThrownBy(() -> jwtProvider.getAuthenticationFromAccessToken(tampered))
			.isInstanceOf(JwtException.class);
	}
}

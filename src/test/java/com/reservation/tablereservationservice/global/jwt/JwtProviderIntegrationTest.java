package com.reservation.tablereservationservice.global.jwt;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.user.UserRole;

@SpringBootTest
@ActiveProfiles("test")
class JwtProviderIntegrationTest {

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("스프링 빈 JwtProvider로 토큰 생성 및 인증 객체 추출 성공")
	void createAndParse_Success() {
		// given
		String email = "tester@email.com";

		// when
		String token = jwtProvider.createAccessToken(email, UserRole.CUSTOMER);
		Authentication authentication = jwtProvider.getAuthenticationFromAccessToken(token);

		// then
		assertThat(token).isNotBlank();
		assertThat(authentication.getPrincipal()).isEqualTo(email);
		assertThat(authentication.getAuthorities())
			.extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_CUSTOMER");
	}
}

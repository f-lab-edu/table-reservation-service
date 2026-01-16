package com.reservation.tablereservationservice.global.jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.JwtAuthenticationException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private JwtAuthenticationEntryPoint authenticationEntryPoint;

	@Mock
	private FilterChain filterChain;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Authorization Bearer 토큰이 유효하면 SecurityContext에 Authentication이 주입되고 체인을 탄다")
	void doFilterInternal_ValidToken_SetsAuthentication_And_Continues() throws Exception {
		// given
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, authenticationEntryPoint);

		String email = "tester@email.com";
		String token = "valid.token.value";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(email, null);

		given(jwtProvider.getAuthenticationFromAccessToken(token)).willReturn(authentication);

		// when
		filter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(email);

	}

	@Test
	@DisplayName("만료 토큰이면 EntryPoint가 EXPIRED_TOKEN으로 호출되고 체인을 중단한다")
	void doFilterInternal_ExpiredToken_CallsEntryPoint_And_Stops() throws Exception {
		// given
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, authenticationEntryPoint);

		String token = "expired.token.value";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(jwtProvider.getAuthenticationFromAccessToken(token))
			.willThrow(new ExpiredJwtException(null, null, "expired"));

		// when
		filter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

		then(authenticationEntryPoint).should().commence(
			any(),
			any(),
			argThat(ex -> ex instanceof JwtAuthenticationException
				&& ((JwtAuthenticationException) ex).getErrorCode() == ErrorCode.EXPIRED_TOKEN)
		);
		then(filterChain).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("위조/잘못된 토큰이면 EntryPoint가 INVALID_ACCESS_TOKEN으로 호출되고 체인을 중단한다")
	void doFilterInternal_InvalidToken_CallsEntryPoint_And_Stops() throws Exception {
		// given
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, authenticationEntryPoint);

		String token = "invalid.token.value";

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(jwtProvider.getAuthenticationFromAccessToken(token))
			.willThrow(new JwtException("invalid"));

		// when
		filter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

		then(authenticationEntryPoint).should().commence(
			any(),
			any(),
			argThat(ex -> ex instanceof JwtAuthenticationException
				&& ((JwtAuthenticationException) ex).getErrorCode() == ErrorCode.INVALID_ACCESS_TOKEN)
		);
		then(filterChain).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("Authorization 헤더가 없거나 Bearer가 아니면 아무 것도 주입하지 않고 체인을 탄다")
	void doFilterInternal_NoBearerHeader_JustContinues() throws Exception {
		// given
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, authenticationEntryPoint);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Basic xxx");
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, filterChain);

		// then
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}

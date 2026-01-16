package com.reservation.tablereservationservice.global.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.config.SecurityConfig;

@WebMvcTest(
	controllers = JwtAuthenticationFilterIntegrationTest.TestMeController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = SecurityConfig.class
	)
)
@Import({
	JwtAuthenticationFilterIntegrationTest.TestMeController.class,
	JwtProvider.class,
	JwtAuthenticationFilterIntegrationTest.TestSecurityConfig.class
})
@ActiveProfiles("test")
class JwtAuthenticationFilterIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("정상 토큰이면 200과 이메일이 반환된다")
	void me_Success() throws Exception {
		String email = "tester@email.com";
		String token = jwtProvider.createAccessToken(email, UserRole.CUSTOMER);

		mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().string(email));
	}

	@Test
	@DisplayName("토큰 없이 요청하면 401을 반환한다")
	void me_Fail_WithoutToken() throws Exception {
		mockMvc.perform(get("/test/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("잘못된 토큰이면 401을 반환한다")
	void me_Fail_InvalidToken() throws Exception {
		mockMvc.perform(get("/test/me").header("Authorization", "Bearer invalid-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Bearer 접두사가 없으면 인증에 실패하여 401을 반환한다")
	void me_Fail_NoBearerPrefix() throws Exception {
		String email = "tester@email.com";
		String token = jwtProvider.createAccessToken(email, UserRole.CUSTOMER);

		mockMvc.perform(get("/test/me").header("Authorization", token)) // Bearer 누락
			.andExpect(status().isUnauthorized());
	}

	@TestConfiguration
	static class TestSecurityConfig {

		@Bean
		@Order(0)
		SecurityFilterChain testFilterChain(HttpSecurity http, JwtProvider jwtProvider) throws Exception {

			JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint() {
				@Override
				public void commence(
					jakarta.servlet.http.HttpServletRequest request,
					jakarta.servlet.http.HttpServletResponse response,
					org.springframework.security.core.AuthenticationException authException
				) throws IOException {
					response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
				}
			};

			JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, entryPoint);

			return http
				.securityMatcher("/test/**")
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
				.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
				.build();
		}
	}

	@RestController
	static class TestMeController {
		@GetMapping("/test/me")
		public String me(org.springframework.security.core.Authentication auth) {
			return auth.getName();
		}
	}
}

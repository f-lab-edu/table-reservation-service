package com.reservation.tablereservationservice.global.jwt;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.JwtAuthenticationException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;
	private final JwtAuthenticationEntryPoint authenticationEntryPoint;

	private static final String BEARER_PREFIX = "Bearer ";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		String token = resolveToken(request);

		if (StringUtils.hasText(token)) {
			try {
				Authentication authentication = jwtProvider.getAuthenticationFromAccessToken(token);
				SecurityContextHolder.getContext().setAuthentication(authentication);

			} catch (ExpiredJwtException e) {
				SecurityContextHolder.clearContext();
				authenticationEntryPoint.commence(request, response,
					new JwtAuthenticationException(ErrorCode.EXPIRED_TOKEN));
				return;
			} catch (JwtException | IllegalArgumentException e) {
				SecurityContextHolder.clearContext();
				authenticationEntryPoint.commence(request, response,
					new JwtAuthenticationException(ErrorCode.INVALID_ACCESS_TOKEN));
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(bearer) || !bearer.startsWith(BEARER_PREFIX)) {
			return null;
		}
		return bearer.substring(BEARER_PREFIX.length());
	}
}

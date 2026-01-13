package com.reservation.tablereservationservice.global.jwt;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.JwtAuthenticationException;

import io.jsonwebtoken.Claims;
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

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		String token = resolveToken(request);

		if (StringUtils.hasText(token)) {
			try {
				Claims claims = jwtProvider.parseClaims(token);

				String email = claims.getSubject();
				String role = (String)claims.get("role");

				List<GrantedAuthority> authorities = Collections.emptyList();
				if (StringUtils.hasText(role)) {
					authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
				}

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email,
					null, authorities);

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
		if (!StringUtils.hasText(bearer) || !bearer.startsWith("Bearer ")) {
			return null;
		}
		return bearer.substring(7);
	}
}
package com.reservation.tablereservationservice.global.jwt;

import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.reservation.tablereservationservice.domain.user.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

	private final SecretKey secretKey;
	private final long expirationMs;

	private static final String CLAIM_ROLE = "role";
	private static final String ROLE_PREFIX = "ROLE_";

	public JwtProvider(
		@Value("${jwt.secret}") String base64SecretKey,
		@Value("${jwt.expiration}") long expirationMs
	) {
		byte[] keyBytes = Decoders.BASE64.decode(base64SecretKey);
		this.secretKey = Keys.hmacShaKeyFor(keyBytes);
		this.expirationMs = expirationMs;
	}

	public String createAccessToken(String email, UserRole role) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
			.setSubject(email)
			.claim(CLAIM_ROLE, role.name())
			.setIssuedAt(now)
			.setExpiration(expiry)
			.signWith(secretKey, SignatureAlgorithm.HS256)
			.compact();
	}

	public Authentication getAuthenticationFromAccessToken(String accessToken) {
		Claims claims = parseClaims(accessToken);

		String email = claims.getSubject();
		List<GrantedAuthority> authorities = extractAuthorities(claims);

		return new UsernamePasswordAuthenticationToken(email, null, authorities);
	}

	private List<GrantedAuthority> extractAuthorities(Claims claims) {
		String role = claims.get(CLAIM_ROLE, String.class);
		if (!StringUtils.hasText(role)) {
			return List.of();
		}
		return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
	}

	private Claims parseClaims(String token) {
		return Jwts.parserBuilder()
			.setSigningKey(secretKey)
			.build()
			.parseClaimsJws(token)
			.getBody();
	}

}
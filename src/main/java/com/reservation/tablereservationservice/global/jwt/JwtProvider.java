package com.reservation.tablereservationservice.global.jwt;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
			.claim("role", role.name())
			.setIssuedAt(now)
			.setExpiration(expiry)
			.signWith(secretKey, SignatureAlgorithm.HS256)
			.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parserBuilder()
			.setSigningKey(secretKey)
			.build()
			.parseClaimsJws(token)
			.getBody();
	}
}
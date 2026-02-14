package com.reservation.tablereservationservice.global.request;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class RequestSequenceGenerator {

	private final AtomicLong seq = new AtomicLong(0);

	public long next() {
		return seq.incrementAndGet();
	}
}

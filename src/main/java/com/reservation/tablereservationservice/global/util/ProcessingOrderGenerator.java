package com.reservation.tablereservationservice.global.util;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class ProcessingOrderGenerator {

	private final AtomicLong counter = new AtomicLong(0);

	public long next() {
		return counter.incrementAndGet();
	}
}

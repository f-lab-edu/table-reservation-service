package com.reservation.tablereservationservice.domain.reservation;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReservationStatusTest {

	@ParameterizedTest(name = "{0} → {1} : 허용")
	@CsvSource({
		"PENDING, CONFIRMED",
		"PENDING, PAYMENT_FAILED",
		"CONFIRMED, CANCEL_PENDING",
		"CANCEL_PENDING, CANCELED"
	})
	@DisplayName("허용된 상태 전이는 canTransitionTo가 true를 반환한다")
	void allowedTransitions(ReservationStatus from, ReservationStatus to) {
		assertThat(from.canTransitionTo(to)).isTrue();
	}

	@ParameterizedTest(name = "{0} → {1} : 거절")
	@CsvSource({
		"PENDING, CANCEL_PENDING",
		"PENDING, CANCELED",
		"CONFIRMED, PENDING",
		"CONFIRMED, CONFIRMED",
		"CONFIRMED, PAYMENT_FAILED",
		"CANCEL_PENDING, PENDING",
		"CANCEL_PENDING, CONFIRMED",
		"CANCELED, PENDING",
		"CANCELED, CONFIRMED",
		"PAYMENT_FAILED, PENDING",
		"PAYMENT_FAILED, CONFIRMED"
	})
	@DisplayName("허용되지 않은 상태 전이는 canTransitionTo가 false를 반환한다")
	void disallowedTransitions(ReservationStatus from, ReservationStatus to) {
		assertThat(from.canTransitionTo(to)).isFalse();
	}

	@Test
	@DisplayName("CANCELED는 어떤 상태로도 전이할 수 없다")
	void canceled_isTerminalState() {
		for (ReservationStatus next : ReservationStatus.values()) {
			assertThat(ReservationStatus.CANCELED.canTransitionTo(next)).isFalse();
		}
	}

	@Test
	@DisplayName("PAYMENT_FAILED는 어떤 상태로도 전이할 수 없다")
	void paymentFailed_isTerminalState() {
		for (ReservationStatus next : ReservationStatus.values()) {
			assertThat(ReservationStatus.PAYMENT_FAILED.canTransitionTo(next)).isFalse();
		}
	}
}

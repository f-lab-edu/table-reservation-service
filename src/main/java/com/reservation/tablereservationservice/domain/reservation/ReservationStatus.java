package com.reservation.tablereservationservice.domain.reservation;

import java.util.Set;

public enum ReservationStatus {

	PENDING {
		@Override
		public Set<ReservationStatus> allowedTransitions() {
			return Set.of(CONFIRMED, PAYMENT_FAILED);
		}
	},
	CONFIRMED {
		@Override
		public Set<ReservationStatus> allowedTransitions() {
			return Set.of(CANCEL_PENDING);
		}
	},
	CANCEL_PENDING {
		@Override
		public Set<ReservationStatus> allowedTransitions() {
			return Set.of(CANCELED);
		}
	},
	CANCELED {
		@Override
		public Set<ReservationStatus> allowedTransitions() {
			return Set.of();
		}
	},
	PAYMENT_FAILED {
		@Override
		public Set<ReservationStatus> allowedTransitions() {
			return Set.of();
		}
	};

	public abstract Set<ReservationStatus> allowedTransitions();

	public boolean canTransitionTo(ReservationStatus next) {
		return allowedTransitions().contains(next);
	}
}

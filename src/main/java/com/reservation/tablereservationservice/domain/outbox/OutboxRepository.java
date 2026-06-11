package com.reservation.tablereservationservice.domain.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository {

	OutboxEvent save(OutboxEvent event);

	List<OutboxEvent> findPendingForUpdate(int limit);

	void markPublished(List<Long> ids, LocalDateTime publishedAt);

	int deletePublishedBefore(LocalDateTime publishedBefore);
}

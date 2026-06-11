package com.reservation.tablereservationservice.infrastructure.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.outbox.OutboxEvent;
import com.reservation.tablereservationservice.domain.outbox.OutboxRepository;
import com.reservation.tablereservationservice.infrastructure.outbox.entity.OutboxEventEntity;
import com.reservation.tablereservationservice.infrastructure.outbox.mapper.OutboxMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaOutboxRepository implements OutboxRepository {

	private final OutboxEntityRepository outboxEntityRepository;

	@Override
	public OutboxEvent save(OutboxEvent event) {
		OutboxEventEntity entity = OutboxMapper.INSTANCE.toEntity(event);
		OutboxEventEntity saved = outboxEntityRepository.save(entity);
		return OutboxMapper.INSTANCE.toDomain(saved);
	}

	@Override
	public List<OutboxEvent> findPendingForUpdate(int limit) {
		Pageable pageable = PageRequest.of(0, limit);
		List<OutboxEventEntity> entities = outboxEntityRepository.findPendingForUpdateSkipLocked(pageable);
		return entities.stream()
				.map(OutboxMapper.INSTANCE::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public void markPublished(List<Long> ids, LocalDateTime publishedAt) {
		if (ids.isEmpty()) {
			return;
		}
		outboxEntityRepository.markPublished(ids, publishedAt);
	}

	@Override
	@Transactional
	public int deletePublishedBefore(LocalDateTime publishedBefore) {
		return outboxEntityRepository.deletePublishedBefore(publishedBefore);
	}
}

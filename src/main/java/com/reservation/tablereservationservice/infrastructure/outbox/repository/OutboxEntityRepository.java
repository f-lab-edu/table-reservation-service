package com.reservation.tablereservationservice.infrastructure.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.infrastructure.outbox.entity.OutboxEventEntity;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxEntityRepository extends JpaRepository<OutboxEventEntity, Long> {

	String SKIP_LOCKED = "-2";

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
	@Query("select o from OutboxEventEntity o where o.status = 'PENDING' order by o.createdAt asc")
	List<OutboxEventEntity> findPendingForUpdateSkipLocked(Pageable pageable);

	@Modifying
	@Query("update OutboxEventEntity o set o.status = 'PUBLISHED', o.publishedAt = :publishedAt where o.id in :ids")
	void markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);

	@Modifying
	@Query("delete from OutboxEventEntity o where o.status = 'PUBLISHED' and o.publishedAt < :publishedBefore")
	int deletePublishedBefore(@Param("publishedBefore") LocalDateTime publishedBefore);
}

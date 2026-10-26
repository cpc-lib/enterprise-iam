package com.enterprise.iam.outbox.mapper;

import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxEventToAppend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OutboxPersistenceMapper {

    int insertOutboxEvent(@Param("event") OutboxEventToAppend event);

    List<Long> claimableIds(@Param("now") Instant now,
                            @Param("batchSize") int batchSize);

    int claimRows(@Param("ids") List<Long> ids,
                  @Param("owner") String owner,
                  @Param("claimUntil") Instant claimUntil);

    List<OutboxEvent> loadClaimedRows(@Param("ids") List<Long> ids,
                                      @Param("owner") String owner);

    int markPublished(@Param("id") long id,
                      @Param("owner") String owner,
                      @Param("publishedAt") Instant publishedAt);

    int reschedule(@Param("id") long id,
                   @Param("owner") String owner,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") Instant nextRetryAt,
                   @Param("errorCode") String errorCode);

    int markDead(@Param("id") long id,
                 @Param("owner") String owner,
                 @Param("retryCount") int retryCount,
                 @Param("errorCode") String errorCode);
}

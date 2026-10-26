package com.enterprise.iam.idempotency.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface IdempotencyPersistenceMapper {

    List<RecordRow> lockRecord(@Param("tenantId") long tenantId,
                               @Param("key") String key);

    int insertClaim(@Param("tenantId") long tenantId,
                    @Param("key") String key,
                    @Param("requestHash") String requestHash,
                    @Param("status") String status,
                    @Param("expiresAt") Instant expiresAt);

    int takeOverExpiredClaim(@Param("tenantId") long tenantId,
                             @Param("key") String key,
                             @Param("requestHash") String requestHash,
                             @Param("status") String status,
                             @Param("expiresAt") Instant expiresAt);

    int commitClaim(@Param("tenantId") long tenantId,
                    @Param("key") String key,
                    @Param("committedStatus") String committedStatus,
                    @Param("responseStatus") int responseStatus,
                    @Param("responseContentType") String responseContentType,
                    @Param("responseBody") byte[] responseBody,
                    @Param("inFlightStatus") String inFlightStatus);

    int releaseClaim(@Param("tenantId") long tenantId,
                     @Param("key") String key,
                     @Param("inFlightStatus") String inFlightStatus);

    record RecordRow(
            String requestHash,
            String status,
            Integer responseStatus,
            String responseContentType,
            byte[] responseBody,
            Instant expiresAt) {
    }
}

package com.enterprise.iam.authorization.infrastructure.persistence;

import com.enterprise.iam.authorization.application.port.out.ResolvedMaskStrategyRepository;
import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.AuthorizationPolicyMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.MaskStrategyRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads ACTIVE, non-deleted mask strategies from {@code iam_mask_strategy}.
 * Rows with malformed config_json are skipped (fail closed): the referencing
 * field grant then carries no typed mask and consumers apply a generic mask.
 */
public final class JdbcResolvedMaskStrategyRepository implements ResolvedMaskStrategyRepository {

    private final AuthorizationPolicyMapper mapper;
    private final ObjectMapper objectMapper;

    public JdbcResolvedMaskStrategyRepository(AuthorizationPolicyMapper mapper, ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResolvedMaskStrategy> findByIds(long tenantId, Collection<Long> strategyIds) {
        if (strategyIds == null || strategyIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = strategyIds.stream().distinct().sorted().toList();
        List<ResolvedMaskStrategy> strategies = new ArrayList<>();
        for (MaskStrategyRow row : mapper.selectMaskStrategies(tenantId, ids)) {
            try {
                Map<String, Object> config = objectMapper.readValue(row.configJson(), Map.class);
                strategies.add(new ResolvedMaskStrategy(
                        row.id(), row.strategyType(), config));
            } catch (Exception e) {
                // Fail closed: malformed config or unknown type means no typed strategy.
            }
        }
        return strategies;
    }
}

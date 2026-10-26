package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.ResolvedMaskStrategy;

import java.util.Collection;
import java.util.List;

/**
 * Read-side port for mask strategy definitions referenced by field grants,
 * used when assembling decision facts. Implementations MUST return only
 * ACTIVE, non-deleted strategies: a referenced strategy that is missing,
 * disabled or soft-deleted is simply absent, so the resulting obligation
 * carries no typed mask and consumers fail closed to a generic mask.
 */
public interface ResolvedMaskStrategyRepository {

    List<ResolvedMaskStrategy> findByIds(long tenantId, Collection<Long> strategyIds);
}

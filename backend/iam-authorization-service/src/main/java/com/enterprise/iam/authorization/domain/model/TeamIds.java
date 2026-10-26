package com.enterprise.iam.authorization.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defensive extraction of the {@code teamIds} request-context list. Team
 * memberships are optional: an absent or empty list simply disables TEAM
 * share matching.
 */
public final class TeamIds {

    public static final String CONTEXT_KEY = "teamIds";
    public static final int MAX_TEAM_IDS = 100;

    private TeamIds() {
    }

    public static List<Long> from(Map<String, Object> context) {
        if (context == null) {
            return List.of();
        }
        Object raw = context.get(CONTEXT_KEY);
        if (raw instanceof List<?> list) {
            List<Long> normalized = new ArrayList<>();
            for (Object element : list) {
                Long teamId = normalize(element);
                if (teamId != null && !normalized.contains(teamId)) {
                    normalized.add(teamId);
                }
            }
            return Collections.unmodifiableList(normalized);
        }
        Long single = normalize(raw);
        return single == null ? List.of() : List.of(single);
    }

    static Long normalize(Object element) {
        if (element instanceof Number number && number.doubleValue() % 1 == 0) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        if (element instanceof String text) {
            try {
                long value = Long.parseLong(text.trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

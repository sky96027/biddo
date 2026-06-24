package com.biddo.domain.report.entity;

import java.util.Set;

public enum ReportStatus {
    PENDING(Set.of("REVIEWED", "DISMISSED")),
    REVIEWED(Set.of("RESOLVED", "DISMISSED")),
    RESOLVED(Set.of()),
    DISMISSED(Set.of());

    private final Set<String> allowedTransitions;

    ReportStatus(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean canTransitionTo(ReportStatus target) {
        return allowedTransitions.contains(target.name());
    }
}
package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/** Campaign offer only; does not initiate or authorize any financial operation. */
public record CompensationTerms(CompensationType type, BigDecimal amount, String currency, String description) {
    public CompensationTerms {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
        if (type == CompensationType.CASH && (amount == null || amount.signum() <= 0 || currency == null)) {
            throw new IllegalArgumentException("Positive amount and currency required for cash");
        }
        if (amount != null && amount.signum() < 0) throw new IllegalArgumentException("Negative amount");
    }
}

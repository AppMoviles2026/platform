package com.collabtech.platform.campaign.domain.model.valueobjects;

import java.math.BigDecimal;

/** Campaign offer only; does not initiate or authorize any financial operation. */
public record CompensationTerms(CompensationType type, BigDecimal amount, String currency, String description) {
    public CompensationTerms {
        if (type == null) throw new IllegalArgumentException("Compensation type required");
        description = CampaignText.required(description, 2000);
        if (type == CompensationType.CASH) {
            if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 10 || currency == null)
                throw new IllegalArgumentException("Cash requires a positive amount with at most two decimals and currency");
            currency = currency.strip().toUpperCase(java.util.Locale.ROOT);
            try { java.util.Currency.getInstance(currency); } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Unsupported currency"); }
            amount = amount.setScale(2);
        } else if (amount != null || currency != null) {
            throw new IllegalArgumentException("Non-cash compensation uses description, not a cash amount");
        }
    }
}

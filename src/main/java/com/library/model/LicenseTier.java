package com.library.model;

public enum LicenseTier {
    BASIC("Basic", "NPR 12,000 / year", "1 computer | Up to 5,000 books | Basic reporting"),
    PRO("Pro", "NPR 30,000 / year", "Networked computers | Unlimited books | Backups + barcode support"),
    ENTERPRISE("Enterprise", "Custom pricing", "PostgreSQL | Premium support | Tailored deployment");

    private final String displayName;
    private final String price;
    private final String summary;

    LicenseTier(String displayName, String price, String summary) {
        this.displayName = displayName;
        this.price = price;
        this.summary = summary;
    }

    public String getDisplayName() { return displayName; }
    public String getPrice() { return price; }
    public String getSummary() { return summary; }

    public static LicenseTier from(String value) {
        for (LicenseTier tier : values()) {
            if (tier.name().equalsIgnoreCase(value) || tier.displayName.equalsIgnoreCase(value)) return tier;
        }
        throw new IllegalArgumentException("Unknown license tier.");
    }
}
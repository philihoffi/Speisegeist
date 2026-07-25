package com.philipphofmann.backend.entity;

/**
 * Dietary restrictions and preferences for recipe generation.
 */
public enum DietaryRestriction {
    VEGAN("Vegan", "Keine tierischen Produkte"),
    VEGETARISCH("Vegetarisch", "Keine Fleisch/Fisch"),
    PESCETARISCH("Pescetarisch", "Kein Fleisch, Fisch OK"),
    GLUTENFREI("Glutenfrei", "Kein Gluten"),
    LAKTOSEFREI("Laktosefrei", "Keine Milchprodukte"),
    NUSSALLERGIKER("Nussallergiker", "Keine Nussprodukte"),
    KETO("Keto", "Low-Carb, High-Fat"),
    LOW_FODMAP("Low-FODMAP", "IBS-freundlich");

    private final String displayName;
    private final String description;

    DietaryRestriction(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

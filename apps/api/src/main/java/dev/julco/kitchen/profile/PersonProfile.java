package dev.julco.kitchen.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Personal profile: follows the person, not the household. Allergies are health data. */
@Entity
@Table(name = "person_profile")
public class PersonProfile {

    @Id
    private UUID userId;

    private String diet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> allergies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> dislikes = new ArrayList<>();

    private String notes;

    private Instant onboardingCompletedAt;

    private Instant healthDataConsentAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected PersonProfile() {}

    public PersonProfile(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDiet() {
        return diet;
    }

    public List<String> getAllergies() {
        return List.copyOf(allergies);
    }

    public List<String> getDislikes() {
        return List.copyOf(dislikes);
    }

    public String getNotes() {
        return notes;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public Instant getHealthDataConsentAt() {
        return healthDataConsentAt;
    }

    public boolean hasHealthDataConsent() {
        return healthDataConsentAt != null;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void setDiet(String diet) {
        this.diet = diet;
    }

    /** Callers check consent first; the database refuses allergies without it anyway (V5). */
    void setAllergies(List<String> allergies) {
        this.allergies = new ArrayList<>(allergies);
    }

    void setDislikes(List<String> dislikes) {
        this.dislikes = new ArrayList<>(dislikes);
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    void completeOnboarding(Instant at) {
        if (onboardingCompletedAt == null) {
            onboardingCompletedAt = at;
        }
    }

    void giveHealthDataConsent(Instant at) {
        if (healthDataConsentAt == null) {
            healthDataConsentAt = at;
        }
    }

    /** Withdrawing consent erases the health data it covered. */
    void withdrawHealthDataConsent() {
        healthDataConsentAt = null;
        allergies = new ArrayList<>();
    }
}

package dev.julco.kitchen.profile;

import dev.julco.kitchen.common.HouseholdScoped;
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

@Entity
@Table(name = "household_profile")
public class HouseholdProfile implements HouseholdScoped {

    @Id
    private UUID householdId;

    private Short tableSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> goals = new ArrayList<>();

    private String goalsNote;

    private String budget;

    private Short maxActiveMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> staples = new ArrayList<>();

    private String notes;

    private Instant onboardingCompletedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected HouseholdProfile() {}

    public HouseholdProfile(UUID householdId) {
        this.householdId = householdId;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public Short getTableSize() {
        return tableSize;
    }

    public List<String> getGoals() {
        return List.copyOf(goals);
    }

    public String getGoalsNote() {
        return goalsNote;
    }

    public String getBudget() {
        return budget;
    }

    public Short getMaxActiveMinutes() {
        return maxActiveMinutes;
    }

    public List<String> getStaples() {
        return List.copyOf(staples);
    }

    public String getNotes() {
        return notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    void setTableSize(Short tableSize) {
        this.tableSize = tableSize;
    }

    void setGoals(List<String> goals) {
        this.goals = new ArrayList<>(goals);
    }

    void setGoalsNote(String goalsNote) {
        this.goalsNote = goalsNote;
    }

    void setBudget(String budget) {
        this.budget = budget;
    }

    void setMaxActiveMinutes(Short maxActiveMinutes) {
        this.maxActiveMinutes = maxActiveMinutes;
    }

    void setStaples(List<String> staples) {
        this.staples = new ArrayList<>(staples);
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    void completeOnboarding(Instant at) {
        if (onboardingCompletedAt == null) {
            onboardingCompletedAt = at;
        }
    }
}

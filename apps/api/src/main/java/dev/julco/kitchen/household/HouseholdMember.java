package dev.julco.kitchen.household;

import dev.julco.kitchen.common.HouseholdScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "household_member")
@IdClass(HouseholdMember.Key.class)
public class HouseholdMember implements HouseholdScoped {

    @Id
    private UUID householdId;

    @Id
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected HouseholdMember() {}

    public HouseholdMember(UUID householdId, UUID userId, MemberRole role) {
        this.householdId = householdId;
        this.userId = userId;
        this.role = role;
    }

    @Override
    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getUserId() {
        return userId;
    }

    public MemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Composite primary key (household_id, user_id). */
    public static class Key implements Serializable {

        private UUID householdId;
        private UUID userId;

        protected Key() {}

        public Key(UUID householdId, UUID userId) {
            this.householdId = householdId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(householdId, key.householdId)
                    && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(householdId, userId);
        }
    }
}

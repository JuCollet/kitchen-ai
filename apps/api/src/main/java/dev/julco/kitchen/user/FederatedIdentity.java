package dev.julco.kitchen.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** An external account (Google) linked to a Kitchen account. */
@Entity
@Table(name = "federated_identity")
@IdClass(FederatedIdentity.Key.class)
public class FederatedIdentity {

    public static final String GOOGLE = "google";

    @Id
    private String provider;

    /** Stable id of the account at the provider ("sub" claim). */
    @Id
    private String subject;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FederatedIdentity() {}

    public FederatedIdentity(String provider, String subject, UUID userId) {
        this.provider = provider;
        this.subject = subject;
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class Key implements Serializable {

        private String provider;
        private String subject;

        protected Key() {}

        public Key(String provider, String subject) {
            this.provider = provider;
            this.subject = subject;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(provider, key.provider)
                    && Objects.equals(subject, key.subject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, subject);
        }
    }
}

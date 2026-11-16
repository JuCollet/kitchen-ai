package dev.julco.kitchen.household;

import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Invitations to join a household: a single-use random token, valid for 7 days, sent by a member
 * to the person they invite (link or code).
 */
@Service
@Validated
@Transactional
public class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final HouseholdInvitationRepository invitations;
    private final HouseholdMemberRepository members;
    private final HouseholdService households;

    public InvitationService(
            HouseholdInvitationRepository invitations,
            HouseholdMemberRepository members,
            HouseholdService households) {
        this.invitations = invitations;
        this.members = members;
        this.households = households;
    }

    /** Any member can invite someone into their own household. */
    public CreatedInvitation createInvitation(@NotNull CurrentMember currentMember) {
        String token = newToken();
        Instant expiresAt = Instant.now().plus(VALIDITY);
        invitations.save(new HouseholdInvitation(
                currentMember.householdId(), hash(token), currentMember.userId(), expiresAt));
        return new CreatedInvitation(token, expiresAt);
    }

    /**
     * Joins the household of the invitation. An unknown, expired or already used token is reported
     * as not found, without telling which.
     */
    public CurrentMember acceptInvitation(
            @NotNull AuthenticatedUser authenticatedUser, @NotBlank @Size(max = 100) String token) {
        Instant now = Instant.now();
        HouseholdInvitation invitation = invitations.findByTokenHash(hash(token.strip()))
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(NotFoundException::new);
        if (members.existsByUserId(authenticatedUser.userId())) {
            throw new AlreadyInHouseholdException();
        }
        members.save(new HouseholdMember(invitation.getHouseholdId(), authenticatedUser.userId(), MemberRole.MEMBER));
        invitation.markUsed(authenticatedUser.userId(), now);
        return households.resolveCurrentMember(authenticatedUser.userId()).orElseThrow();
    }

    /** 256 random bits, URL-safe: 43 characters. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 is enough here: the token is random, not a password that could be guessed. */
    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}

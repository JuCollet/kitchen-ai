package dev.julco.kitchen.household;

import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.AppUserRepository;
import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Households and membership. Business service: no dependency on Spring Web or the MCP SDK
 * (CLAUDE.md rule 3), so the site API and the MCP tools can both call it.
 */
@Service
@Validated
@Transactional
public class HouseholdService {

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final AppUserRepository users;

    public HouseholdService(
            HouseholdRepository households, HouseholdMemberRepository members, AppUserRepository users) {
        this.households = households;
        this.members = members;
        this.users = users;
    }

    /**
     * Resolves the current member from an authenticated user id. Empty when the user does not exist
     * or does not belong to a household yet (onboarding: create or join one).
     *
     * <p>The user id must come from the session or the token, never from the request content.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentMember> resolveCurrentMember(@NotNull UUID authenticatedUserId) {
        return users.findById(authenticatedUserId)
                .flatMap(user -> members.findByUserId(user.getId())
                        .map(member -> new CurrentMember(
                                user.getId(), member.getHouseholdId(), member.getRole(), user.getLocale())));
    }

    /** Creates a household with the user as its owner. */
    public CurrentMember createHousehold(
            @NotNull UUID authenticatedUserId, @NotBlank @Size(max = 100) String name) {
        AppUser user = users.findById(authenticatedUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (members.existsByUserId(user.getId())) {
            throw new AlreadyInHouseholdException();
        }
        Household household = households.save(new Household(name.strip()));
        members.save(new HouseholdMember(household.getId(), user.getId(), MemberRole.OWNER));
        return new CurrentMember(user.getId(), household.getId(), MemberRole.OWNER, user.getLocale());
    }

    /** The current member's household. */
    @Transactional(readOnly = true)
    public HouseholdSummary getHousehold(@NotNull CurrentMember currentMember) {
        Household household = households.findById(currentMember.householdId()).orElseThrow();
        return new HouseholdSummary(household.getId(), household.getName(), currentMember.role());
    }

    /** The household of an authenticated user, if they belong to one. */
    @Transactional(readOnly = true)
    public Optional<HouseholdSummary> findHousehold(@NotNull AuthenticatedUser authenticatedUser) {
        return resolveCurrentMember(authenticatedUser.userId()).map(this::getHousehold);
    }

    /** Members of the current member's household, oldest first. */
    @Transactional(readOnly = true)
    public List<MemberSummary> listMembers(@NotNull CurrentMember currentMember) {
        return members.findSummariesByHouseholdId(currentMember.householdId());
    }
}

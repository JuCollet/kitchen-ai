package dev.julco.kitchen.profile;

import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.household.MemberSummary;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.AuthenticatedUser;
import dev.julco.kitchen.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Personal and household profiles, and consent for health data. Personal data is always read and
 * written by the current member's user id; household data by their household id (CLAUDE.md rule 1).
 */
@Service
@Validated
@Transactional
public class ProfileService {

    private final PersonProfileRepository personProfiles;
    private final HouseholdProfileRepository householdProfiles;
    private final HouseholdService households;
    private final UserService users;

    public ProfileService(
            PersonProfileRepository personProfiles,
            HouseholdProfileRepository householdProfiles,
            HouseholdService households,
            UserService users) {
        this.personProfiles = personProfiles;
        this.householdProfiles = householdProfiles;
        this.households = households;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PersonProfileView getMyProfile(@NotNull CurrentMember currentMember) {
        AppUser user = users.getUser(new AuthenticatedUser(currentMember.userId()));
        PersonProfile profile = personProfiles.findByUserId(currentMember.userId())
                .orElseGet(() -> new PersonProfile(currentMember.userId()));
        return new PersonProfileView(
                user.getDisplayName(),
                user.getLocale(),
                profile.getDiet(),
                profile.getAllergies(),
                profile.getDislikes(),
                profile.getNotes(),
                profile.hasHealthDataConsent(),
                profile.getOnboardingCompletedAt() != null);
    }

    public PersonProfileView updateMyProfile(
            @NotNull CurrentMember currentMember, @NotNull @Valid PersonProfileUpdate update) {
        PersonProfile profile = personProfile(currentMember.userId());
        if (update.allergies() != null && !update.allergies().isEmpty() && !profile.hasHealthDataConsent()) {
            throw new HealthDataConsentRequiredException();
        }
        if (update.diet() != null) {
            profile.setDiet(blankToNull(update.diet()));
        }
        if (update.allergies() != null) {
            profile.setAllergies(clean(update.allergies()));
        }
        if (update.dislikes() != null) {
            profile.setDislikes(clean(update.dislikes()));
        }
        if (update.notes() != null) {
            profile.setNotes(blankToNull(update.notes()));
        }
        if (Boolean.TRUE.equals(update.onboardingComplete())) {
            profile.completeOnboarding(Instant.now());
        }
        personProfiles.save(profile);
        if (update.language() != null) {
            users.changeLanguage(currentMember.userId(), update.language());
        }
        return getMyProfile(currentMember);
    }

    @Transactional(readOnly = true)
    public HouseholdProfileView getHouseholdProfile(@NotNull CurrentMember currentMember) {
        return view(householdProfiles.findByHouseholdId(currentMember.householdId())
                .orElseGet(() -> new HouseholdProfile(currentMember.householdId())));
    }

    public HouseholdProfileView updateHouseholdProfile(
            @NotNull CurrentMember currentMember, @NotNull @Valid HouseholdProfileUpdate update) {
        HouseholdProfile profile = householdProfiles.findByHouseholdId(currentMember.householdId())
                .orElseGet(() -> new HouseholdProfile(currentMember.householdId()));
        if (update.tableSize() != null) {
            profile.setTableSize(update.tableSize().shortValue());
        }
        if (update.goals() != null) {
            profile.setGoals(clean(update.goals()));
        }
        if (update.goalsNote() != null) {
            profile.setGoalsNote(blankToNull(update.goalsNote()));
        }
        if (update.budget() != null) {
            profile.setBudget(blankToNull(update.budget()));
        }
        if (update.maxActiveMinutes() != null) {
            profile.setMaxActiveMinutes(update.maxActiveMinutes().shortValue());
        }
        if (update.staples() != null) {
            profile.setStaples(clean(update.staples()));
        }
        if (update.notes() != null) {
            profile.setNotes(blankToNull(update.notes()));
        }
        if (Boolean.TRUE.equals(update.onboardingComplete())) {
            profile.completeOnboarding(Instant.now());
        }
        return view(householdProfiles.save(profile));
    }

    /** Dietary constraints of every member of the current member's household, oldest member first. */
    @Transactional(readOnly = true)
    public List<MemberConstraints> listMemberConstraints(@NotNull CurrentMember currentMember) {
        List<MemberSummary> members = households.listMembers(currentMember);
        Map<UUID, PersonProfile> profiles = personProfiles
                .findAllByUserIdIn(members.stream().map(MemberSummary::userId).toList())
                .stream()
                .collect(Collectors.toMap(PersonProfile::getUserId, Function.identity()));
        return members.stream()
                .map(member -> {
                    PersonProfile profile = profiles.getOrDefault(member.userId(), new PersonProfile(member.userId()));
                    return new MemberConstraints(
                            member.userId(),
                            member.displayName(),
                            profile.getDiet(),
                            profile.getAllergies(),
                            profile.getDislikes());
                })
                .toList();
    }

    // Health data consent: given on the site, possibly before the person joins a household.

    @Transactional(readOnly = true)
    public boolean hasHealthDataConsent(@NotNull AuthenticatedUser authenticatedUser) {
        return personProfiles.findByUserId(authenticatedUser.userId())
                .map(PersonProfile::hasHealthDataConsent)
                .orElse(false);
    }

    public void giveHealthDataConsent(@NotNull AuthenticatedUser authenticatedUser) {
        PersonProfile profile = personProfile(authenticatedUser.userId());
        profile.giveHealthDataConsent(Instant.now());
        personProfiles.save(profile);
    }

    /** Erases the allergies along with the consent. */
    public void withdrawHealthDataConsent(@NotNull AuthenticatedUser authenticatedUser) {
        personProfiles.findByUserId(authenticatedUser.userId()).ifPresent(profile -> {
            profile.withdrawHealthDataConsent();
            personProfiles.save(profile);
        });
    }

    private PersonProfile personProfile(UUID userId) {
        return personProfiles.findByUserId(userId).orElseGet(() -> new PersonProfile(userId));
    }

    private static HouseholdProfileView view(HouseholdProfile profile) {
        return new HouseholdProfileView(
                profile.getTableSize(),
                profile.getGoals(),
                profile.getGoalsNote(),
                profile.getBudget(),
                profile.getMaxActiveMinutes(),
                profile.getStaples(),
                profile.getNotes(),
                profile.getOnboardingCompletedAt() != null);
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }

    /** Trimmed, without duplicates (case-insensitive), in the given order. */
    private static List<String> clean(List<String> values) {
        Map<String, String> unique = new LinkedHashMap<>();
        values.forEach(value -> unique.putIfAbsent(value.strip().toLowerCase(Locale.ROOT), value.strip()));
        return List.copyOf(unique.values());
    }
}

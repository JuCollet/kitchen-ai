package dev.julco.kitchen.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProfileServiceTest {

    @Autowired EntityManager em;
    @Autowired ProfileService profiles;
    @Autowired HouseholdService households;

    HouseholdFixture a;
    HouseholdFixture b;
    CurrentMember alice;
    CurrentMember bob;

    @BeforeEach
    void createTwoHouseholds() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
        alice = households.resolveCurrentMember(a.userId()).orElseThrow();
        bob = households.resolveCurrentMember(b.userId()).orElseThrow();
    }

    @Test
    void only_given_fields_change_and_blank_values_clear_them() {
        profiles.updateMyProfile(alice, update("vegetarian", null, List.of("Olives", " olives ", "liver"), "notes"));

        PersonProfileView profile = profiles.updateMyProfile(alice, update(null, null, null, " "));

        assertThat(profile.diet()).isEqualTo("vegetarian");
        assertThat(profile.dislikes()).containsExactly("Olives", "liver");
        assertThat(profile.notes()).isNull();
        assertThat(profile.onboardingComplete()).isFalse();
    }

    @Test
    void language_and_onboarding_completion_are_saved() {
        profiles.updateMyProfile(alice, new PersonProfileUpdate(null, null, null, null, Language.NL, true));

        PersonProfileView profile = profiles.getMyProfile(alice);
        assertThat(profile.language()).isEqualTo(Language.NL);
        assertThat(profile.onboardingComplete()).isTrue();
    }

    @Test
    void allergies_need_consent_given_on_the_site() {
        assertThatThrownBy(() -> profiles.updateMyProfile(alice, update(null, List.of("peanuts"), null, null)))
                .isInstanceOf(HealthDataConsentRequiredException.class);
        // An empty list is no health data: always accepted.
        profiles.updateMyProfile(alice, update(null, List.of(), null, null));

        profiles.giveHealthDataConsent(new AuthenticatedUser(alice.userId()));
        assertThat(profiles.updateMyProfile(alice, update(null, List.of("peanuts"), null, null)).allergies())
                .containsExactly("peanuts");
    }

    @Test
    void withdrawing_consent_erases_allergies() {
        AuthenticatedUser user = new AuthenticatedUser(alice.userId());
        profiles.giveHealthDataConsent(user);
        profiles.updateMyProfile(alice, update(null, List.of("peanuts"), null, null));

        profiles.withdrawHealthDataConsent(user);

        assertThat(profiles.hasHealthDataConsent(user)).isFalse();
        assertThat(profiles.getMyProfile(alice).allergies()).isEmpty();
    }

    @Test
    void inputs_are_bounded() {
        assertThatThrownBy(() -> profiles.updateMyProfile(alice, update("x".repeat(201), null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> profiles.updateMyProfile(
                alice, update(null, null, Collections.nCopies(51, "olives"), null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> profiles.updateHouseholdProfile(alice,
                new HouseholdProfileUpdate(21, null, null, null, null, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void household_profile_is_shared_by_the_household_only() {
        profiles.updateHouseholdProfile(alice,
                new HouseholdProfileUpdate(4, List.of("eat healthier"), null, "100 EUR per week", 30,
                        List.of("rice"), null, true));

        HouseholdProfileView mine = profiles.getHouseholdProfile(alice);
        assertThat(mine.tableSize()).isEqualTo((short) 4);
        assertThat(mine.onboardingComplete()).isTrue();
        HouseholdProfileView theirs = profiles.getHouseholdProfile(bob);
        assertThat(theirs.tableSize()).isNull();
        assertThat(theirs.onboardingComplete()).isFalse();
    }

    @Test
    void member_constraints_never_include_another_household() {
        profiles.updateMyProfile(alice, update("vegan", null, null, null));

        assertThat(profiles.listMemberConstraints(bob))
                .extracting(MemberConstraints::userId)
                .containsExactly(b.userId());
        assertThat(profiles.listMemberConstraints(alice))
                .singleElement()
                .satisfies(member -> assertThat(member.diet()).isEqualTo("vegan"));
    }

    private static PersonProfileUpdate update(String diet, List<String> allergies, List<String> dislikes, String notes) {
        return new PersonProfileUpdate(diet, allergies, dislikes, notes, null, null);
    }
}

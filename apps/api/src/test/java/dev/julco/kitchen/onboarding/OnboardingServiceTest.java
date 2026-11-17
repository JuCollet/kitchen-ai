package dev.julco.kitchen.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import dev.julco.kitchen.profile.HouseholdProfileUpdate;
import dev.julco.kitchen.profile.PersonProfileUpdate;
import dev.julco.kitchen.profile.ProfileService;
import dev.julco.kitchen.user.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class OnboardingServiceTest {

    @Autowired EntityManager em;
    @Autowired OnboardingService onboarding;
    @Autowired ProfileService profiles;
    @Autowired HouseholdService households;

    CurrentMember alice;

    @BeforeEach
    void createHousehold() {
        HouseholdFixture a = HouseholdFixture.create(em, "Alice");
        alice = households.resolveCurrentMember(a.userId()).orElseThrow();
    }

    @Test
    void a_new_person_gets_personal_then_household_questions_in_their_language() {
        OnboardingState state = onboarding.state(alice);

        assertThat(state.personalComplete()).isFalse();
        assertThat(state.householdComplete()).isFalse();
        assertThat(state.nextQuestions())
                .extracting(OnboardingQuestion::id)
                .containsExactly(
                        "personal.diet", "personal.allergies", "personal.dislikes",
                        "household.table_size", "household.goals", "household.max_active_minutes",
                        "household.budget", "household.staples", "household.appliances");
        // The fixture person speaks French, and their household already has an appliance.
        assertThat(state.nextQuestions().getFirst().text()).startsWith("Suivez-vous un régime");
        assertThat(state.nextQuestions().get(8).text()).isEqualTo("Avez-vous un autre appareil de cuisson à ajouter ?");
    }

    @Test
    void without_consent_the_allergy_question_points_to_the_account_page() {
        assertThat(question("personal.allergies"))
                .contains("données de santé")
                .contains("http://localhost:5173/account");

        profiles.giveHealthDataConsent(new AuthenticatedUser(alice.userId()));

        assertThat(question("personal.allergies")).startsWith("Avez-vous des allergies");
    }

    @Test
    void questions_follow_a_language_change() {
        profiles.updateMyProfile(alice, new PersonProfileUpdate(null, null, null, null, Language.NL, null));
        em.flush();
        CurrentMember dutch = households.resolveCurrentMember(alice.userId()).orElseThrow();

        assertThat(onboarding.state(dutch).nextQuestions().getFirst().text()).startsWith("Volgt u een bepaald dieet");
    }

    @Test
    void completed_parts_are_not_asked_again() {
        profiles.updateMyProfile(alice, new PersonProfileUpdate(null, null, null, null, null, true));
        assertThat(onboarding.state(alice).nextQuestions().getFirst().id()).isEqualTo("household.table_size");

        profiles.updateHouseholdProfile(alice, new HouseholdProfileUpdate(null, null, null, null, null, null, null, true));
        OnboardingState state = onboarding.state(alice);
        assertThat(state.personalComplete()).isTrue();
        assertThat(state.householdComplete()).isTrue();
        assertThat(state.nextQuestions()).isEmpty();
    }

    private String question(String id) {
        return onboarding.state(alice).nextQuestions().stream()
                .filter(question -> question.id().equals(id))
                .findFirst()
                .orElseThrow()
                .text();
    }
}

package dev.julco.kitchen.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HouseholdServiceTest {

    @Autowired EntityManager em;
    @Autowired HouseholdService service;

    @Test
    void a_user_without_household_has_no_current_member() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        assertThat(service.resolveCurrentMember(user.getId())).isEmpty();
        assertThat(service.resolveCurrentMember(UUID.randomUUID())).isEmpty();
    }

    @Test
    void creating_a_household_makes_the_user_its_owner() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        CurrentMember created = service.createHousehold(user.getId(), "  Our home  ");

        assertThat(created.userId()).isEqualTo(user.getId());
        assertThat(created.isOwner()).isTrue();
        assertThat(created.language()).isEqualTo(Language.NL);
        assertThat(service.resolveCurrentMember(user.getId())).contains(created);
        assertThat(service.listMembers(created))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.userId()).isEqualTo(user.getId());
                    assertThat(member.role()).isEqualTo(MemberRole.OWNER);
                });
    }

    @Test
    void a_user_cannot_create_a_second_household() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");
        service.createHousehold(user.getId(), "First");

        assertThatThrownBy(() -> service.createHousehold(user.getId(), "Second"))
                .isInstanceOf(AlreadyInHouseholdException.class);
    }

    @Test
    void household_names_are_validated() {
        AppUser user = HouseholdFixture.createUser(em, "Nina");

        assertThatThrownBy(() -> service.createHousehold(user.getId(), " "))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.createHousehold(user.getId(), "x".repeat(101)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void members_of_another_household_are_never_listed() {
        HouseholdFixture a = HouseholdFixture.create(em, "Alice");
        HouseholdFixture b = HouseholdFixture.create(em, "Bob");
        CurrentMember bob = service.resolveCurrentMember(b.userId()).orElseThrow();

        assertThat(service.listMembers(bob))
                .extracting(MemberSummary::userId)
                .containsExactly(b.userId())
                .doesNotContain(a.userId());
    }
}

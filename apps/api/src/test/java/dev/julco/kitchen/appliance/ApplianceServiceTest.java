package dev.julco.kitchen.appliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.julco.kitchen.HouseholdFixture;
import dev.julco.kitchen.TestcontainersConfiguration;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.household.HouseholdService;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
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
class ApplianceServiceTest {

    @Autowired EntityManager em;
    @Autowired ApplianceService appliances;
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
    void a_confirmed_sheet_is_saved_with_who_confirmed_it() {
        ApplianceView saved = appliances.save(alice, input(null, spec(mode("bake", 50, 250)), true));

        assertThat(saved.usagePreference()).isEqualTo(UsagePreference.DEFAULT);
        assertThat(appliances.list(alice)).extracting(ApplianceView::name).contains("Oven", "Speedi");
        Appliance stored = em.find(Appliance.class, saved.id());
        assertThat(stored.getConfirmedBy()).isEqualTo(alice.userId());
        assertThat(stored.getConfirmedAt()).isNotNull();
    }

    @Test
    void an_unconfirmed_sheet_is_refused() {
        assertThatThrownBy(() -> appliances.save(alice, input(null, spec(mode("bake", 50, 250)), false)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("confirm");
    }

    @Test
    void sheets_need_valid_unique_modes() {
        assertThatThrownBy(() -> appliances.save(alice, input(null, spec(), true)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> appliances.save(alice, input(null, spec(mode("Air Fry", 150, 200)), true)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> appliances.save(alice, input(null, spec(mode("bake", 50, 250), mode("bake", 60, 200)), true)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> appliances.save(alice, input(null, spec(mode("bake", 250, 50)), true)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("temp_min");
    }

    @Test
    void another_household_appliance_cannot_be_read_or_changed() {
        assertThat(appliances.list(bob)).extracting(ApplianceView::id).doesNotContain(a.applianceId());

        assertThatThrownBy(() -> appliances.save(bob, input(a.applianceId(), spec(mode("bake", 50, 250)), true)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> appliances.setUsagePreference(bob, a.applianceId(), UsagePreference.ON_REQUEST))
                .isInstanceOf(NotFoundException.class);
        assertThat(em.find(Appliance.class, a.applianceId()).getName()).isEqualTo("Speedi");
    }

    @Test
    void the_usage_preference_can_be_changed() {
        assertThat(appliances.setUsagePreference(alice, a.applianceId(), UsagePreference.ON_REQUEST).usagePreference())
                .isEqualTo(UsagePreference.ON_REQUEST);
    }

    private static ApplianceInput input(java.util.UUID id, ApplianceSpec spec, boolean confirmed) {
        return new ApplianceInput(id, "Oven", "Brand X", spec, UsagePreference.DEFAULT, confirmed);
    }

    private static ApplianceSpec spec(ApplianceMode... modes) {
        return new ApplianceSpec(List.of(modes), "60 L", null);
    }

    private static ApplianceMode mode(String name, int min, int max) {
        return new ApplianceMode(name, min, max, null);
    }
}

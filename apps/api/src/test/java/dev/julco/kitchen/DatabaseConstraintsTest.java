package dev.julco.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarantees enforced by PostgreSQL itself, whatever the Java code does. Raw SQL on purpose: these
 * tests simulate a buggy service that would bypass the repositories.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseConstraintsTest {

    /** Every table that holds household data, with its household column. */
    private static final List<String> HOUSEHOLD_TABLES = List.of(
            "household_member", "household_invitation", "household_profile", "appliance", "recipe",
            "recipe_rating", "menu", "menu_item", "shopping_list", "shopping_list_item", "pantry_item",
            "pdf_export");

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    HouseholdFixture a;
    HouseholdFixture b;

    @BeforeEach
    void createTwoHouseholds() {
        a = HouseholdFixture.create(em, "Alice");
        b = HouseholdFixture.create(em, "Bob");
    }

    @Test
    void a_menu_cannot_reference_a_recipe_of_another_household() {
        jdbc.update("""
                insert into menu_item (id, household_id, menu_id, served_on, meal, recipe_id, servings)
                values (?, ?, ?, date '2026-11-17', 'dinner', ?, 2)""",
                UUID.randomUUID(), b.householdId(), b.menuId(), a.recipeId());

        // This foreign key is deferred: PostgreSQL checks it at commit, forced here.
        assertThatThrownBy(this::checkDeferredConstraints)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void a_recipe_version_cannot_belong_to_a_recipe_of_another_household() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into recipe_version (id, household_id, recipe_id, version, content)
                values (?, ?, ?, 1, '{}')""", UUID.randomUUID(), b.householdId(), a.recipeId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void a_pantry_has_one_item_per_name_whatever_the_case() {
        // The same name in two households is fine (first: PostgreSQL aborts the transaction on a violation).
        jdbc.update("insert into pantry_item (id, household_id, name, source) values (?, ?, 'Basmati', 'manual')",
                UUID.randomUUID(), b.householdId());
        jdbc.update("insert into pantry_item (id, household_id, name, source) values (?, ?, 'basmati', 'manual')",
                UUID.randomUUID(), a.householdId());

        assertThatThrownBy(() -> jdbc.update(
                "insert into pantry_item (id, household_id, name, source) values (?, ?, 'RICE', 'manual')",
                UUID.randomUUID(), a.householdId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pantry_item_name_idx");
    }

    @Test
    void a_recipe_used_in_a_menu_cannot_be_deleted() {
        jdbc.update("delete from recipe where id = ?", a.recipeId());

        assertThatThrownBy(this::checkDeferredConstraints)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void a_recipe_cannot_use_an_appliance_of_another_household() {
        assertThatThrownBy(() -> jdbc.update(
                "update recipe set appliance_id = ? where id = ?", a.applianceId(), b.recipeId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void a_shopping_list_cannot_reference_a_menu_of_another_household() {
        assertThatThrownBy(() -> jdbc.update(
                "update shopping_list set menu_id = ? where id = ?", a.menuId(), b.shoppingListId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void only_members_of_the_household_can_rate_its_recipes() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into recipe_rating (id, household_id, recipe_id, user_id, score)
                values (?, ?, ?, ?, 7)""",
                UUID.randomUUID(), a.householdId(), a.recipeId(), b.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("foreign key");
    }

    @Test
    void a_person_belongs_to_a_single_household() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into household_member (household_id, user_id, role) values (?, ?, 'member')",
                b.householdId(), a.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("household_member_user_uk");
    }

    @Test
    void ratings_are_out_of_ten() {
        assertThatThrownBy(() -> jdbc.update("update recipe_rating set score = 11 where id = ?", a.ratingId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleting_a_household_deletes_all_its_data_and_nothing_else() {
        jdbc.update("delete from household where id = ?", a.householdId());
        checkDeferredConstraints();

        for (String table : HOUSEHOLD_TABLES) {
            assertThat(count(table, a.householdId())).as(table).isZero();
            assertThat(count(table, b.householdId())).as(table).isPositive();
        }
    }

    @Test
    void leaving_a_household_takes_personal_ratings_along_but_keeps_recipes() {
        jdbc.update("delete from household_member where user_id = ?", a.userId());

        assertThat(count("recipe_rating", a.householdId())).isZero();
        assertThat(count("recipe", a.householdId())).isOne();
        assertThat(count("recipe_rating", b.householdId())).isOne();
    }

    @Test
    void allergies_are_never_stored_without_consent() {
        assertThatThrownBy(() -> jdbc.update(
                "update person_profile set allergies = '[\"peanuts\"]' where user_id = ?", a.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("person_profile_allergies_need_consent");
    }

    @Test
    void json_columns_use_snake_case_and_lowercase_codes() {
        String ingredients = jdbc.queryForObject(
                "select ingredients::text from recipe where id = ?", String.class, a.recipeId());
        String steps = jdbc.queryForObject("select steps::text from recipe where id = ?", String.class, a.recipeId());
        String spec = jdbc.queryForObject("select spec::text from appliance where id = ?", String.class, a.applianceId());

        assertThat(ingredients).contains("\"aisle\": \"meat_fish\"");
        assertThat(steps).contains("\"duration_min\": 20").contains("\"appliance_mode\": \"air_fry\"");
        assertThat(spec).contains("\"temp_min\": 150").contains("\"temp_max\": 210");
    }

    /** Tests roll back instead of committing: run the commit-time checks explicitly. */
    private void checkDeferredConstraints() {
        jdbc.execute("set constraints all immediate");
    }

    private int count(String table, UUID householdId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from " + table + " where household_id = ?", Integer.class, householdId);
        return count == null ? 0 : count;
    }
}

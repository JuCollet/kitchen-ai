package dev.julco.kitchen.menu;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Recipes served recently in the household's menus that the connected person has not rated
 * since: what the AI offers to rate at the start of a conversation.
 *
 * <p>Isolation (CLAUDE.md rule 1): the household id is the first condition of every table read,
 * and the ratings looked at are the person's own (user id). Both come from the current member.
 * Only {@link MenuService} calls this class.
 */
@Component
class RecipesToRateQuery {

    private final JdbcClient jdbc;

    RecipesToRateQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<RecipeToRate> find(UUID householdId, UUID userId, LocalDate from, LocalDate to) {
        // A rating counts if it was given for the day of the meal or later (a rating with a date
        // only is stored at noon UTC of that day, RecipeService.rate).
        return jdbc.sql("""
                        select recipe_id, title, served_on, meal
                        from (
                            select distinct on (mi.recipe_id) mi.recipe_id, r.title, mi.served_on, mi.meal
                            from menu_item mi
                            join recipe r on r.household_id = mi.household_id and r.id = mi.recipe_id
                            where mi.household_id = :householdId
                              and mi.served_on between :from and :to
                              and not exists (
                                  select 1 from recipe_rating rr
                                  where rr.household_id = :householdId
                                    and rr.recipe_id = mi.recipe_id
                                    and rr.user_id = :userId
                                    and rr.cooked_at >= mi.served_on::timestamp at time zone 'UTC')
                            order by mi.recipe_id, mi.served_on desc
                        ) latest
                        order by served_on desc, title
                        """)
                .param("householdId", householdId)
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> new RecipeToRate(
                        rs.getObject("recipe_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("served_on", LocalDate.class),
                        Meal.fromCode(rs.getString("meal"))))
                .list();
    }
}

package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The recipe search, in SQL: full-text search (V6), latest rating per person, household average
 * and last cooking date are easier to read as one query than as JPA criteria.
 *
 * <p>Isolation (CLAUDE.md rule 1): the household id is a mandatory argument and the first
 * condition of every subquery; it comes from the current member, never from the criteria. The
 * criteria only add bound parameters, never SQL text. Only {@link RecipeService} calls this class.
 */
@Component
class RecipeSearchQuery {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    RecipeSearchQuery(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * When each recipe was last cooked: its latest rating, or its latest meal in a menu up to
     * today, whichever is more recent. A menu meal counts as cooked at noon UTC of its day, like
     * a rating given with a date only (RecipeService.rate).
     */
    private static final String COOKED = """
            cooked as (
                select recipe_id, max(at) as last_cooked_at
                from (
                    select recipe_id, cooked_at as at
                    from recipe_rating
                    where household_id = :householdId
                    union all
                    select recipe_id, (served_on + time '12:00') at time zone 'UTC'
                    from menu_item
                    where household_id = :householdId and served_on <= :today
                ) events
                group by recipe_id
            )
            """;

    RecipeSearchPage search(UUID householdId, UUID userId, RecipeSearch criteria) {
        StringBuilder sql = new StringBuilder("""
                with latest as (
                    -- Each member's latest rating of each recipe.
                    select distinct on (recipe_id, user_id) recipe_id, user_id, score, cooked_at
                    from recipe_rating
                    where household_id = :householdId
                    order by recipe_id, user_id, cooked_at desc, id desc
                ),
                scores as (
                    select recipe_id,
                           round(avg(score), 1) as household_score,
                           count(*) as rater_count,
                           max(score) filter (where user_id = :userId) as my_score
                    from latest
                    group by recipe_id
                ),
                """ + COOKED + """
                select r.id, r.title, r.summary, r.appliance_id, r.tags::text as tags, r.servings,
                       r.total_minutes, r.version, s.my_score, s.household_score, s.rater_count,
                       c.last_cooked_at
                from recipe r
                left join scores s on s.recipe_id = r.id
                left join cooked c on c.recipe_id = r.id
                where r.household_id = :householdId
                """);
        List<String> order = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("householdId", householdId);
        params.put("userId", userId);
        params.put("today", today());

        if (criteria.text() != null && !criteria.text().isBlank()) {
            sql.append("""
                    and r.search_vector @@ websearch_to_tsquery(
                            kitchen_search_config(r.language), kitchen_unaccent(:text))
                    """);
            order.add("""
                    ts_rank(r.search_vector,
                            websearch_to_tsquery(kitchen_search_config(r.language), kitchen_unaccent(:text))) desc""");
            params.put("text", criteria.text().strip());
        }
        if (criteria.tags() != null && !criteria.tags().isEmpty()) {
            // Every requested tag must be present (tags are stored lowercase).
            sql.append("and r.tags @> cast(:tags as jsonb)\n");
            params.put("tags", json.writeValueAsString(criteria.tags().stream()
                    .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                    .toList()));
        }
        if (criteria.applianceId() != null) {
            sql.append("and r.appliance_id = :applianceId\n");
            params.put("applianceId", criteria.applianceId());
        }
        if (Boolean.TRUE.equals(criteria.classicOnly())) {
            sql.append("and r.appliance_id is null\n");
        }
        if (criteria.minMyScore() != null) {
            sql.append("and s.my_score >= :minMyScore\n");
            params.put("minMyScore", criteria.minMyScore());
        }
        if (criteria.minHouseholdScore() != null) {
            sql.append("and s.household_score >= :minHouseholdScore\n");
            params.put("minHouseholdScore", criteria.minHouseholdScore());
        }
        if (criteria.notCookedForDays() != null) {
            sql.append("and (c.last_cooked_at is null or c.last_cooked_at < now() - make_interval(days => :days))\n");
            params.put("days", criteria.notCookedForDays());
        }

        order.add("r.title");
        order.add("r.id");
        int size = criteria.sizeOrDefault();
        int page = criteria.pageOrFirst();
        sql.append("order by ").append(String.join(", ", order)).append("\nlimit :limit offset :offset");
        // One more row than asked: tells whether there is a next page without counting.
        params.put("limit", size + 1);
        params.put("offset", page * size);

        List<RecipeSummary> rows = jdbc.sql(sql.toString()).params(params).query(this::summary).list();
        boolean hasMore = rows.size() > size;
        return new RecipeSearchPage(hasMore ? rows.subList(0, size) : rows, page, hasMore);
    }

    Instant lastCookedAt(UUID householdId, UUID recipeId) {
        return jdbc.sql("with " + COOKED + "select last_cooked_at from cooked where recipe_id = :recipeId")
                .param("householdId", householdId)
                .param("today", today())
                .param("recipeId", recipeId)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant)
                .orElse(null);
    }

    /** Dates are UTC, like every date Kitchen stores. */
    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private RecipeSummary summary(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastCooked = rs.getTimestamp("last_cooked_at");
        return new RecipeSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getObject("appliance_id", UUID.class),
                json.readValue(rs.getString("tags"), STRINGS),
                rs.getShort("servings"),
                rs.getObject("total_minutes", Integer.class),
                rs.getInt("version"),
                rs.getObject("my_score", Integer.class),
                rs.getObject("household_score", BigDecimal.class),
                rs.getInt("rater_count"),
                lastCooked == null ? null : lastCooked.toInstant());
    }
}

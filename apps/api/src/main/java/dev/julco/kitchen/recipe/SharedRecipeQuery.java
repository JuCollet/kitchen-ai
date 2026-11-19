package dev.julco.kitchen.recipe;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Search among published recipes, with their average rating, in SQL (like RecipeSearchQuery).
 *
 * <p>Isolation, deliberately different from the rest of Kitchen: published recipes are readable by
 * every household, and the average reads ratings across households. It only ever returns, per
 * published recipe, the average and the number of ratings: never a rating, a person or a household.
 * The connected household is used to leave out its own publications from the search and to find
 * its own copy ({@code importedAs}). The criteria only add bound parameters, never SQL text. Only
 * {@link SharingService} calls this class.
 */
@Component
class SharedRecipeQuery {

    /**
     * The recipes behind a publication: its source recipe in the origin household, and every copy
     * imported by another household. Their ratings make the average: the latest rating of each
     * person on each of those recipes.
     */
    private static final String SCORES = """
            copies as (
                select c.id as shared_id, r.household_id, r.id as recipe_id
                from candidates c
                join recipe r on r.shared_recipe_id = c.id
                union all
                select c.id, r.household_id, r.id
                from candidates c
                join recipe r on r.household_id = c.origin_household_id and r.id = c.source_recipe_id
            ),
            latest as (
                select distinct on (cp.shared_id, rr.recipe_id, rr.user_id) cp.shared_id, rr.score
                from copies cp
                join recipe_rating rr on rr.household_id = cp.household_id and rr.recipe_id = cp.recipe_id
                order by cp.shared_id, rr.recipe_id, rr.user_id, rr.cooked_at desc, rr.id desc
            ),
            scores as (
                select shared_id, round(avg(score), 2) as average_rating, count(*) as rating_count
                from latest
                group by shared_id
            )
            """;

    /**
     * The name a publication shows, read now rather than copied at publication: the household's
     * current name, or the person's current display name (none once their account is deleted).
     * Null when the publication is anonymous.
     */
    private static final String AUTHOR = """
            case s.signature when 'household' then h.name when 'person' then u.display_name end""";

    /** Publications with their author's name; the WHERE clause is added by the caller. */
    private static final String PUBLICATIONS = "select s.*, " + AUTHOR + " as author\n" + """
            from shared_recipe s
            join household h on h.id = s.origin_household_id
            left join app_user u on u.id = s.published_by
            """;

    private static final String SELECT = """
            select c.id, c.title, c.language, c.author, c.content::text as content, c.published_at,
                   c.origin_household_id = :householdId as mine,
                   s.average_rating, coalesce(s.rating_count, 0) as rating_count,
                   (select r.id from recipe r
                    where r.household_id = :householdId and r.shared_recipe_id = c.id
                    order by r.created_at limit 1) as imported_as
            from candidates c
            left join scores s on s.shared_id = c.id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    SharedRecipeQuery(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Live publications of other households matching the criteria, best match first, then newest. */
    SharedRecipePage search(UUID householdId, SharedRecipeSearch criteria, String notice) {
        StringBuilder candidates = new StringBuilder(PUBLICATIONS).append("""
                where s.withdrawn_at is null and s.origin_household_id <> :householdId
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("householdId", householdId);
        List<String> order = new ArrayList<>();
        if (criteria.text() != null && !criteria.text().isBlank()) {
            candidates.append("""
                    and s.search_vector @@ websearch_to_tsquery(kitchen_search_config(s.language), kitchen_unaccent(:text))
                    """);
            order.add("ts_rank(c.search_vector, websearch_to_tsquery(kitchen_search_config(c.language), "
                    + "kitchen_unaccent(:text))) desc");
            params.put("text", criteria.text().strip());
        }
        if (criteria.tags() != null && !criteria.tags().isEmpty()) {
            candidates.append("and s.content -> 'tags' @> cast(:tags as jsonb)\n");
            params.put("tags", json.writeValueAsString(
                    criteria.tags().stream().map(tag -> tag.strip().toLowerCase(Locale.ROOT)).toList()));
        }
        if (criteria.language() != null) {
            candidates.append("and s.language = :language\n");
            params.put("language", criteria.language());
        }
        if (criteria.author() != null && !criteria.author().isBlank()) {
            // A plain substring test (position), not LIKE: no wildcard in the person's text matters.
            candidates.append("""
                    and position(kitchen_unaccent(lower(:author))
                                 in kitchen_unaccent(lower(""").append(AUTHOR).append(")\n)) > 0\n");
            params.put("author", criteria.author().strip());
        }
        order.add("c.published_at desc");
        order.add("c.id");
        int page = criteria.pageOrFirst();
        params.put("limit", SharedRecipeSearch.PAGE_SIZE + 1);
        params.put("offset", page * SharedRecipeSearch.PAGE_SIZE);

        String sql = "with candidates as (" + candidates + "),\n" + SCORES + SELECT
                + "order by " + String.join(", ", order) + "\nlimit :limit offset :offset";
        List<SharedRecipeSummary> rows = jdbc.sql(sql).params(params).query(this::summary).list();
        boolean hasMore = rows.size() > SharedRecipeSearch.PAGE_SIZE;
        return new SharedRecipePage(
                hasMore ? rows.subList(0, SharedRecipeSearch.PAGE_SIZE) : rows, page, hasMore, notice);
    }

    /** One live publication (of any household, the connected one included). */
    Optional<SharedRecipeDetail> get(UUID householdId, UUID sharedRecipeId, String notice) {
        String sql = "with candidates as (" + PUBLICATIONS + "where s.id = :id and s.withdrawn_at is null),\n"
                + SCORES + SELECT;
        return jdbc.sql(sql)
                .param("householdId", householdId)
                .param("id", sharedRecipeId)
                .query((rs, rowNum) -> new SharedRecipeDetail(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("language"),
                        rs.getString("author"),
                        content(rs),
                        rs.getBigDecimal("average_rating"),
                        rs.getInt("rating_count"),
                        rs.getObject("imported_as", UUID.class),
                        rs.getBoolean("mine"),
                        rs.getTimestamp("published_at").toInstant(),
                        notice))
                .optional();
    }

    private SharedRecipeSummary summary(ResultSet rs, int rowNum) throws SQLException {
        SharedRecipeContent content = content(rs);
        BigDecimal average = rs.getBigDecimal("average_rating");
        return new SharedRecipeSummary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("language"),
                rs.getString("author"),
                content.summary(),
                content.tags() == null ? List.of() : content.tags(),
                content.servings(),
                content.totalMinutes() == null ? null : content.totalMinutes().intValue(),
                content.applianceModel(),
                average,
                rs.getInt("rating_count"),
                rs.getObject("imported_as", UUID.class),
                rs.getTimestamp("published_at").toInstant());
    }

    private SharedRecipeContent content(ResultSet rs) throws SQLException {
        return json.readValue(rs.getString("content"), SharedRecipeContent.class);
    }
}

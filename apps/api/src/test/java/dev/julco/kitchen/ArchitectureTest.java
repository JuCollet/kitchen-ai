package dev.julco.kitchen;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.julco.kitchen.common.HouseholdScoped;
import dev.julco.kitchen.common.UnscopedQuery;
import dev.julco.kitchen.household.HouseholdRepository;
import dev.julco.kitchen.user.AppUserRepository;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;

/** Architecture rules from CLAUDE.md, checked on the production code. */
@AnalyzeClasses(packages = "dev.julco.kitchen", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Rule 3: business services can be called from the site API and the MCP tools alike. */
    @ArchTest
    static final ArchRule services_depend_neither_on_web_nor_on_mcp = noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "jakarta.servlet..",
                    "io.modelcontextprotocol..",
                    "org.springframework.ai..")
            .because("business services must not depend on Spring Web or the MCP SDK (CLAUDE.md rule 3)");

    /**
     * Rule 1: CrudRepository and its siblings expose findById, findAll and deleteById, which read
     * or delete rows without any household filter. Repositories extend the empty Repository
     * interface and declare only filtered methods.
     */
    @ArchTest
    static final ArchRule repositories_do_not_expose_unfiltered_crud = noClasses()
            .that().areAssignableTo(Repository.class)
            .should().beAssignableTo(CrudRepository.class)
            .orShould().beAssignableTo(ListCrudRepository.class)
            .orShould().beAssignableTo(PagingAndSortingRepository.class)
            .orShould().beAssignableTo(ListPagingAndSortingRepository.class)
            .because("generic CRUD methods bypass the household filter (CLAUDE.md rule 1)");

    /**
     * Rule 1: every read or delete of household data is filtered by household or user id, unless
     * the method is explicitly marked {@link UnscopedQuery} with a justification.
     */
    @ArchTest
    static final ArchRule household_scoped_repositories_filter_every_query = classes()
            .that().areAssignableTo(Repository.class)
            .and().areInterfaces()
            .should(filterHouseholdScopedQueries());

    /** Rule 1: only identity repositories may look a row up by its id alone. */
    @ArchTest
    static final ArchRule only_identity_repositories_find_by_id_alone = classes()
            .that().areAssignableTo(Repository.class)
            .and().areInterfaces()
            .and().doNotBelongToAnyOf(AppUserRepository.class, HouseholdRepository.class)
            .should(notDeclareMethodsNamed(Set.of("findById", "deleteById", "existsById", "findAll")));

    /** Rule 3: MCP tools and controllers are thin adapters; data access goes through services. */
    @ArchTest
    static final ArchRule repositories_are_only_used_by_services = noClasses()
            .that().areNotAnnotatedWith(Service.class)
            .and().areNotAssignableTo(Repository.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .because("only business services access repositories (CLAUDE.md rule 3)");

    /** Packages by feature: dependencies between features must stay one-way. */
    @ArchTest
    static final ArchRule feature_packages_are_free_of_cycles =
            slices().matching("dev.julco.kitchen.(*)..").should().beFreeOfCycles();

    private static ArchCondition<JavaClass> filterHouseholdScopedQueries() {
        return new ArchCondition<>("filter every query by householdId or userId when the entity is household-scoped") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                if (domainType(repository).filter(type -> type.isAssignableTo(HouseholdScoped.class)).isEmpty()) {
                    return;
                }
                for (JavaMethod method : repository.getMethods()) {
                    String name = method.getName();
                    boolean filtered = name.contains("HouseholdId") || name.contains("UserId");
                    boolean explicitException = method.isAnnotatedWith(UnscopedQuery.class);
                    if (!name.equals("save") && !filtered && !explicitException) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " reads household data without filtering by householdId or userId"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDeclareMethodsNamed(Set<String> forbidden) {
        return new ArchCondition<>("not declare " + forbidden) {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                for (JavaMethod method : repository.getMethods()) {
                    if (forbidden.contains(method.getName())) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " accesses business data by id alone (CLAUDE.md rule 1)"));
                    }
                }
            }
        };
    }

    /** The entity type T of a {@code Repository<T, ID>}. */
    private static Optional<JavaClass> domainType(JavaClass repository) {
        return repository.getInterfaces().stream()
                .filter(JavaParameterizedType.class::isInstance)
                .map(JavaParameterizedType.class::cast)
                .filter(type -> type.toErasure().isEquivalentTo(Repository.class))
                .map(type -> type.getActualTypeArguments().getFirst().toErasure())
                .findFirst();
    }
}

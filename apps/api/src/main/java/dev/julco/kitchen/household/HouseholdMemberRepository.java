package dev.julco.kitchen.household;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface HouseholdMemberRepository extends Repository<HouseholdMember, HouseholdMember.Key> {

    HouseholdMember save(HouseholdMember member);

    /** A person belongs to at most one household (unique constraint on user_id). */
    Optional<HouseholdMember> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("""
            select new dev.julco.kitchen.household.MemberSummary(u.id, u.displayName, m.role, m.joinedAt)
            from HouseholdMember m join AppUser u on u.id = m.userId
            where m.householdId = :householdId
            order by m.joinedAt""")
    List<MemberSummary> findSummariesByHouseholdId(UUID householdId);
}

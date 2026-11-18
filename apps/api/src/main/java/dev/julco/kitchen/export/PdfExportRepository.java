package dev.julco.kitchen.export;

import dev.julco.kitchen.common.UnscopedQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PdfExportRepository extends Repository<PdfExport, UUID> {

    PdfExport save(PdfExport export);

    Optional<PdfExport> findByIdAndHouseholdId(UUID id, UUID householdId);

    @UnscopedQuery("A download link carries no session: the export is found by the hash of its random "
            + "token, and the household is then taken from the export itself (ExportService.render).")
    Optional<PdfExport> findByTokenHashAndExpiresAtAfter(byte[] tokenHash, Instant now);

    @UnscopedQuery("Cleanup job: expired links of every household are deleted, nothing is read.")
    @Modifying
    @Query("delete from PdfExport e where e.expiresAt < :now")
    int deleteExpired(Instant now);
}

package dev.julco.kitchen.export;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface PdfExportRepository extends Repository<PdfExport, UUID> {

    PdfExport save(PdfExport export);

    Optional<PdfExport> findByIdAndHouseholdId(UUID id, UUID householdId);
}

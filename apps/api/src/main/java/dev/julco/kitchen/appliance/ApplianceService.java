package dev.julco.kitchen.appliance;

import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Household appliances and their spec sheets. There is no catalogue in code: the AI writes the
 * sheet, the person confirms it, Kitchen stores it (docs/SCOPE.md, "Appliances").
 */
@Service
@Validated
@Transactional
public class ApplianceService {

    /** Far more than any kitchen: only there to bound the input. */
    static final int MAX_APPLIANCES = 20;

    private final ApplianceRepository appliances;

    public ApplianceService(ApplianceRepository appliances) {
        this.appliances = appliances;
    }

    @Transactional(readOnly = true)
    public List<ApplianceView> list(@NotNull CurrentMember currentMember) {
        return appliances.findAllByHouseholdIdOrderByName(currentMember.householdId()).stream()
                .map(ApplianceView::of)
                .toList();
    }

    /** Creates or replaces an appliance. Its sheet becomes the only source of truth for its modes. */
    public ApplianceView save(@NotNull CurrentMember currentMember, @NotNull @Valid ApplianceInput input) {
        checkModes(input.spec());
        Appliance appliance;
        if (input.id() == null) {
            if (appliances.countByHouseholdId(currentMember.householdId()) >= MAX_APPLIANCES) {
                throw new InvalidInputException("A household has at most " + MAX_APPLIANCES + " appliances.");
            }
            appliance = new Appliance(
                    currentMember.householdId(), input.name().strip(), blankToNull(input.brandModel()), input.spec());
        } else {
            appliance = find(currentMember, input.id());
            appliance.replace(input.name().strip(), blankToNull(input.brandModel()), input.spec());
        }
        if (input.usagePreference() != null) {
            appliance.setUsagePreference(input.usagePreference());
        }
        appliance.confirm(currentMember.userId(), Instant.now());
        return ApplianceView.of(appliances.save(appliance));
    }

    public ApplianceView setUsagePreference(
            @NotNull CurrentMember currentMember, @NotNull UUID applianceId, @NotNull UsagePreference preference) {
        Appliance appliance = find(currentMember, applianceId);
        appliance.setUsagePreference(preference);
        return ApplianceView.of(appliances.save(appliance));
    }

    @Transactional(readOnly = true)
    public long count(@NotNull CurrentMember currentMember) {
        return appliances.countByHouseholdId(currentMember.householdId());
    }

    /** Unknown and other household's appliances are reported the same way (CLAUDE.md rule 2). */
    private Appliance find(CurrentMember currentMember, UUID applianceId) {
        return appliances.findByIdAndHouseholdId(applianceId, currentMember.householdId())
                .orElseThrow(NotFoundException::new);
    }

    private static void checkModes(ApplianceSpec spec) {
        Set<String> names = new HashSet<>();
        for (ApplianceMode mode : spec.modes()) {
            if (!names.add(mode.name())) {
                throw new InvalidInputException("Mode names must be unique: " + mode.name() + " appears twice.");
            }
            if (mode.tempMin() != null && mode.tempMax() != null && mode.tempMin() > mode.tempMax()) {
                throw new InvalidInputException("Mode " + mode.name() + ": temp_min is above temp_max.");
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

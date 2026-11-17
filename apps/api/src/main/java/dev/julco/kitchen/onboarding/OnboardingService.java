package dev.julco.kitchen.onboarding;

import dev.julco.kitchen.appliance.ApplianceService;
import dev.julco.kitchen.household.CurrentMember;
import dev.julco.kitchen.profile.HouseholdProfileView;
import dev.julco.kitchen.profile.PersonProfileView;
import dev.julco.kitchen.profile.ProfileService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * The conversational onboarding is driven by the server (docs/SCOPE.md): Kitchen decides what is
 * still missing and gives the AI the questions, translated (messages*.properties), so that every
 * AI client asks the same things in the same order. Personal questions first, then the household
 * ones, until a member marks the household part complete.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class OnboardingService {

    private static final List<String> PERSONAL = List.of("diet", "allergies", "dislikes");
    private static final List<String> HOUSEHOLD =
            List.of("table_size", "goals", "max_active_minutes", "budget", "staples", "appliances");

    private final ProfileService profiles;
    private final ApplianceService appliances;
    private final MessageSource messages;
    private final String accountUrl;

    public OnboardingService(
            ProfileService profiles,
            ApplianceService appliances,
            MessageSource messages,
            @Value("${kitchen.public-url}") String publicUrl) {
        this.profiles = profiles;
        this.appliances = appliances;
        this.messages = messages;
        this.accountUrl = publicUrl + "/account";
    }

    public OnboardingState state(@NotNull CurrentMember currentMember) {
        PersonProfileView person = profiles.getMyProfile(currentMember);
        HouseholdProfileView household = profiles.getHouseholdProfile(currentMember);
        Locale locale = Locale.forLanguageTag(person.language().code());

        List<OnboardingQuestion> questions = new ArrayList<>();
        if (!person.onboardingComplete()) {
            for (String id : PERSONAL) {
                String key = id.equals("allergies") && !person.healthDataConsent()
                        ? "onboarding.personal.allergies.no_consent"
                        : "onboarding.personal." + id;
                questions.add(question("personal." + id, key, locale));
            }
        }
        if (!household.onboardingComplete()) {
            for (String id : HOUSEHOLD) {
                if (id.equals("appliances") && appliances.count(currentMember) > 0) {
                    questions.add(question("household.appliances", "onboarding.household.appliances.more", locale));
                } else {
                    questions.add(question("household." + id, "onboarding.household." + id, locale));
                }
            }
        }
        return new OnboardingState(person.onboardingComplete(), household.onboardingComplete(), questions);
    }

    private OnboardingQuestion question(String id, String key, Locale locale) {
        return new OnboardingQuestion(id, messages.getMessage(key, new Object[] {accountUrl}, locale));
    }
}

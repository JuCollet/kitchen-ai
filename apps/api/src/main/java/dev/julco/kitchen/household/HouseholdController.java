package dev.julco.kitchen.household;

import dev.julco.kitchen.common.Language;
import dev.julco.kitchen.user.AppUser;
import dev.julco.kitchen.user.AuthenticatedUser;
import dev.julco.kitchen.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site API for the account, the household and invitations. Thin adapter: every rule lives in the
 * services. {@link AuthenticatedUser} and {@link CurrentMember} are resolved from the session by
 * the auth package, never from the request content.
 */
@RestController
@RequestMapping("/api")
class HouseholdController {

    private final UserService users;
    private final HouseholdService households;
    private final InvitationService invitations;

    HouseholdController(UserService users, HouseholdService households, InvitationService invitations) {
        this.users = users;
        this.households = households;
        this.invitations = invitations;
    }

    @GetMapping("/me")
    MeResponse me(AuthenticatedUser authenticatedUser) {
        AppUser user = users.getUser(authenticatedUser);
        return new MeResponse(
                new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getLocale()),
                households.findHousehold(authenticatedUser).map(HouseholdResponse::of).orElse(null));
    }

    @PostMapping("/households")
    @ResponseStatus(HttpStatus.CREATED)
    HouseholdResponse createHousehold(
            AuthenticatedUser authenticatedUser, @Valid @RequestBody CreateHouseholdRequest request) {
        CurrentMember member = households.createHousehold(authenticatedUser.userId(), request.name());
        return HouseholdResponse.of(households.getHousehold(member));
    }

    @GetMapping("/household/members")
    List<MemberResponse> members(CurrentMember currentMember) {
        return households.listMembers(currentMember).stream().map(MemberResponse::of).toList();
    }

    @PostMapping("/household/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse createInvitation(CurrentMember currentMember) {
        CreatedInvitation invitation = invitations.createInvitation(currentMember);
        return new InvitationResponse(invitation.token(), invitation.expiresAt());
    }

    /** The token travels in the body, not in the URL, so that it does not end up in access logs. */
    @PostMapping("/invitations/accept")
    HouseholdResponse acceptInvitation(
            AuthenticatedUser authenticatedUser, @Valid @RequestBody AcceptInvitationRequest request) {
        CurrentMember member = invitations.acceptInvitation(authenticatedUser, request.token());
        return HouseholdResponse.of(households.getHousehold(member));
    }

    record CreateHouseholdRequest(@NotBlank @Size(max = 100) String name) {}

    record AcceptInvitationRequest(@NotBlank @Size(max = 100) String token) {}

    record UserResponse(UUID id, String email, String displayName, Language locale) {}

    record HouseholdResponse(UUID id, String name, String role) {
        static HouseholdResponse of(HouseholdSummary household) {
            return new HouseholdResponse(household.id(), household.name(), household.role().code());
        }
    }

    record MeResponse(UserResponse user, HouseholdResponse household) {}

    record MemberResponse(UUID userId, String displayName, String role, Instant joinedAt) {
        static MemberResponse of(MemberSummary member) {
            return new MemberResponse(member.userId(), member.displayName(), member.role().code(), member.joinedAt());
        }
    }

    record InvitationResponse(String token, Instant expiresAt) {}
}

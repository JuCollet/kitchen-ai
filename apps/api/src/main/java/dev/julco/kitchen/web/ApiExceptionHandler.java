package dev.julco.kitchen.web;

import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.AlreadyInHouseholdException;
import dev.julco.kitchen.user.EmailAlreadyUsedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps business exceptions to HTTP statuses. Responses carry a stable error code, never an
 * exception message or stack trace (CLAUDE.md rule 7). The front end translates the code.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /** Unknown resource and resource of another household: same answer (CLAUDE.md rule 2). */
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(AlreadyInHouseholdException.class)
    ProblemDetail alreadyInHousehold() {
        return problem(HttpStatus.CONFLICT, "already_in_household");
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ProblemDetail emailAlreadyUsed() {
        return problem(HttpStatus.CONFLICT, "email_already_used");
    }

    @ExceptionHandler(InvalidInputException.class)
    ProblemDetail invalidInput() {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    /** Bean Validation on service method parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalid() {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private static ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setProperty("code", code);
        return problem;
    }
}

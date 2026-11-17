package dev.julco.kitchen.mcp;

import dev.julco.kitchen.auth.CurrentUsers;
import dev.julco.kitchen.common.InvalidInputException;
import dev.julco.kitchen.common.NotFoundException;
import dev.julco.kitchen.household.CurrentMember;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the body of an MCP tool for the current member and turns its outcome into a tool result.
 *
 * <p>Errors are written for the AI, so that it can correct itself, and never carry an exception
 * message the code did not write on purpose: Spring AI would otherwise send any exception message
 * (SQL details, internal names) to the client (CLAUDE.md rules 7 and 8).
 */
@Component
public class ToolRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRunner.class);

    private final CurrentUsers currentUsers;
    private final JsonMapper json;
    private final String publicUrl;

    ToolRunner(CurrentUsers currentUsers, JsonMapper json, @Value("${kitchen.public-url}") String publicUrl) {
        this.currentUsers = currentUsers;
        this.json = json;
        this.publicUrl = publicUrl;
    }

    /**
     * @param notFound what the AI should do when the body throws {@link NotFoundException}, for
     *     example "Appliance not found: use list_appliances to get the ids."
     */
    public CallToolResult run(String notFound, Function<CurrentMember, Object> body) {
        CurrentMember member = currentUsers.currentMember().orElse(null);
        if (member == null) {
            return error("This person does not belong to a Kitchen household yet. They can create one, "
                    + "or join one with an invitation link from a member, on the Kitchen website: " + publicUrl);
        }
        try {
            return success(body.apply(member));
        } catch (NotFoundException e) {
            return error(notFound);
        } catch (ConstraintViolationException e) {
            return error("Invalid arguments: " + describe(e) + ".");
        } catch (InvalidInputException e) {
            return error(e.getMessage());
        } catch (RuntimeException e) {
            // The type only: the message could contain personal data.
            log.error("MCP tool failed with {}", e.getClass().getName());
            return error("Kitchen had an internal error. Nothing was saved; try again later.");
        }
    }

    /**
     * Structured content for clients that read it, and the same JSON as text for the others (MCP
     * specification: a tool returning structured content should also return it serialised). The
     * structured content must be a JSON object: tools wrap lists in a record.
     */
    private CallToolResult success(Object data) {
        Object structured = json.convertValue(data, Object.class);
        if (!(structured instanceof Map)) {
            // The MCP specification requires an object: clients reject an array or a scalar. A bug
            // in the tool, reported like any other internal error.
            throw new IllegalStateException("Tool result is not a JSON object");
        }
        return CallToolResult.builder()
                .addTextContent(json.writeValueAsString(data))
                .structuredContent(structured)
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    /** "spec.modes[0].name: must be a lowercase code like air_fry", without the Java method name. */
    private static String describe(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(violation -> field(violation) + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private static String field(ConstraintViolation<?> violation) {
        // Path: method name, parameter name, then the fields inside the parameter.
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .skip(2)
                .map(ToolRunner::node)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    /**
     * Hibernate Validator puts the index of a list element on the node that follows the list
     * ("modes", then "name" with index 0): written back as "modes[0].name".
     */
    private static String node(Path.Node node) {
        String name = node.getName() == null || node.getName().startsWith("<") ? "" : node.getName();
        if (node.getIndex() == null) {
            return name;
        }
        return "[" + node.getIndex() + "]" + (name.isEmpty() ? "" : "." + name);
    }
}

package dev.julco.kitchen.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method of a household-scoped entity that deliberately does not filter by
 * household, for example a lookup by a secret token. The architecture tests accept it only with
 * this annotation, so every exception is explicit and reviewed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UnscopedQuery {

    /** Why this query is safe without a household filter. */
    String value();
}

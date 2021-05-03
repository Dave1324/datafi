package dev.sanda.datafi.annotations.finders;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * When a field within a given entity is annotated with @FindBy, resolvers are generated in both the
 * data and web layers to fetch a collection of the given entity by the value of the annotated field (passed as an argument).
 */

@Target({ FIELD, TYPE, ANNOTATION_TYPE, METHOD })
@Retention(RUNTIME)
public @interface FindAllBy {
}

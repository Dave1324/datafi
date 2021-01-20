package dev.sanda.datafi.annotations.finders;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Target({FIELD, ANNOTATION_TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FindByUnique {
}

package dev.sanda.datafi.annotations.finders;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ FIELD, TYPE, ANNOTATION_TYPE, METHOD })
@Retention(RUNTIME)
public @interface FindBy {
}

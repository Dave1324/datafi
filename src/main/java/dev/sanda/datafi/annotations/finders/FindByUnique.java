package dev.sanda.datafi.annotations.finders;

import static java.lang.annotation.ElementType.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ FIELD, ANNOTATION_TYPE, METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface FindByUnique {
}

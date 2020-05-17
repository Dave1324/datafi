package dev.sanda.datafi.annotations.free_text_search;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithFreeTextSearchByFields {
    String[] value();
    @AliasFor("value")
    String[] fields() default "";
}

package dev.sanda.datafi.annotations.query;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(WithNativeQueryAccumulator.class)
public @interface WithNativeQuery {
    String name();

    String sql();
}

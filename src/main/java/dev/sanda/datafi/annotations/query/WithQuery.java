package dev.sanda.datafi.annotations.query;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(WithQueryAccumulator.class)
public @interface WithQuery {
  String name();

  String jpql();
}

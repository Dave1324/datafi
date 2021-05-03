package dev.sanda.datafi.reflection.cached_type_info;

import java.lang.reflect.Field;
import lombok.SneakyThrows;

@lombok.Getter
@lombok.Setter
public class CachedEntityField {

  private final Field field;
  private final boolean isCollectionOrMap;
  private final boolean isNonApiUpdatable;
  private final boolean isNonNullable;
  private final boolean isString;

  public CachedEntityField(
    Field field,
    boolean isCollectionOrMap,
    boolean isNonApiUpdatable,
    boolean isNonNullable
  ) {
    this.field = field;
    this.isCollectionOrMap = isCollectionOrMap;
    this.isNonApiUpdatable = isNonApiUpdatable;
    this.isNonNullable = isNonNullable;
    this.isString = field.getType().equals(String.class);
  }

  @SneakyThrows
  public Object getJsonValue(Object instance) {
    field.setAccessible(true);
    return isString ? "\"" + field.get(instance) + "\"" : field.get(instance);
  }
}

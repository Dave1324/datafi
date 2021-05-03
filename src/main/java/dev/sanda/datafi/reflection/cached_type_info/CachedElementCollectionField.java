package dev.sanda.datafi.reflection.cached_type_info;

import java.lang.reflect.Field;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;

@Data
@AllArgsConstructor
@SuppressWarnings("unchecked")
public class CachedElementCollectionField {

  private Field field;

  public void addAll(Object ownerInstance, Collection toAdd) {
    try {
      val fieldValue = (Collection) field.get(ownerInstance);
      fieldValue.addAll(toAdd);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public void removeAll(Object ownerInstance, Collection toRemove) {
    try {
      val fieldValue = (Collection) field.get(ownerInstance);
      fieldValue.removeAll(toRemove);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}

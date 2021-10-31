package dev.sanda.datafi.reflection.cached_type_info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo.genDefaultInstance;

@Data
@AllArgsConstructor
public class CachedMapElementCollectionField {

  private Field field;

  @SuppressWarnings("unchecked")
  public <TKey, TValue> Map<TKey, TValue> getAllByKey(
    Object ownerInstance,
    Collection<TKey> keys
  ) {
    try {
      Map fieldValue = (Map) field.get(ownerInstance);
      if (fieldValue == null) initNullMap(ownerInstance);
      fieldValue = (Map) field.get(ownerInstance);
      val result = new LinkedHashMap<TKey, TValue>();
      for (TKey k : keys) result.put(k, (TValue) fieldValue.get(k));
      return result;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void initNullMap(Object ownerInstance) throws IllegalAccessException {
    if (field.getType().equals(Map.class)) field.set(
      ownerInstance,
      new HashMap<>()
    ); else field.set(ownerInstance, genDefaultInstance(field.getType()));
  }
}

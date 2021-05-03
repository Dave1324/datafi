package dev.sanda.datafi.reflection.runtime_services;

import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CollectionsTypeResolver {

  private final Map<String, Class> collectionsTypes;

  public Class resolveFor(String key) {
    return collectionsTypes.get(key);
  }
}

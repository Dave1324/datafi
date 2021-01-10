package dev.sanda.datafi.reflection.runtime_services;

import lombok.AllArgsConstructor;

import java.util.Map;

@AllArgsConstructor
public class CollectionsTypeResolver {
    private final Map<String, Class> collectionsTypes;
    public Class resolveFor(String key){
        return collectionsTypes.get(key);
    }
}

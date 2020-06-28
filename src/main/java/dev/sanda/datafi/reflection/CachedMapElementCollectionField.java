package dev.sanda.datafi.reflection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.val;
import lombok.var;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.sanda.datafi.reflection.CachedEntityTypeInfo.genDefaultInstance;

@Data
@AllArgsConstructor
@SuppressWarnings("unchecked")
public class CachedMapElementCollectionField {

    private Field field;

    public <TKey, TValue> Map<TKey, TValue> getAllByKey(Object ownerInstance, Collection<TKey> keys){
        try {
            var fieldValue = (Map)field.get(ownerInstance);
            if(fieldValue == null) initNullMap(ownerInstance);
            fieldValue = (Map)field.get(ownerInstance);
            val result = new LinkedHashMap<TKey, TValue>();
            for (TKey k : keys)
                result.put(k, (TValue) fieldValue.get(k));
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void putAll(Object ownerInstance, Map toPut){
        try {
            var fieldValue = (Map) this.field.get(ownerInstance);
            if(fieldValue == null) initNullMap(ownerInstance);
            fieldValue = (Map) this.field.get(ownerInstance);
            fieldValue.putAll(toPut);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeAll(Object ownerInstance, Collection toRemove){
        try {
            var fieldValue = (Map) this.field.get(ownerInstance);
            if(fieldValue == null) initNullMap(ownerInstance);
            fieldValue = (Map) this.field.get(ownerInstance);
            fieldValue.keySet().removeAll(toRemove);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void initNullMap(Object ownerInstance) throws IllegalAccessException {
        if(field.getType().equals(Map.class)) field.set(ownerInstance, new HashMap<>());
        else field.set(ownerInstance, genDefaultInstance(field.getType()));
    }
}

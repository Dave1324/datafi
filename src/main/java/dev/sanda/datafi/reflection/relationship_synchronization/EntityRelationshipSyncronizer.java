package dev.sanda.datafi.reflection.relationship_synchronization;

import dev.sanda.datafi.annotations.attributes.AutoSynchronized;
import dev.sanda.datafi.reflection.runtime_services.CollectionsTypeResolver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo.genDefaultInstance;
import static dev.sanda.datafi.reflection.relationship_synchronization.BackpointerType.*;

@Data
@Slf4j
@SuppressWarnings("unchecked")
public class EntityRelationshipSyncronizer {

    public boolean trySetBackpointer(Field sourceField, Object thisInstance, Object toPointTo){
        try {
            val backpointerType = resolveBackpointerType(sourceField);
            boolean success = false;
            Field targetField;
            String fieldTypeName = toPointTo.getClass().getSimpleName();
            switch (Objects.requireNonNull(backpointerType)){
                case ONE_TO_MANY:{
                    if(!oneToManyBackpointers.containsKey(fieldTypeName)) break;
                    targetField = oneToManyBackpointers.get(fieldTypeName);
                    val targetFieldValue = getOrInstantiateCollection(targetField, thisInstance);
                    targetFieldValue.add(toPointTo);
                    success = true;
                }
                break;
                case MANY_TO_MANY:{
                    if(!manyToManyBackpointers.containsKey(fieldTypeName)) break;
                    targetField = manyToManyBackpointers.get(fieldTypeName);
                    val targetFieldValue = getOrInstantiateCollection(targetField, thisInstance);
                    targetFieldValue.add(toPointTo);
                    success = true;
                }
                break;
                case MANY_TO_ONE:{
                    if(!manyToOneBackpointers.containsKey(fieldTypeName)) break;
                    targetField = manyToOneBackpointers.get(fieldTypeName);
                    targetField.set(thisInstance, toPointTo);
                    success = true;
                }
                case ONE_TO_ONE:{
                    if(!oneToOneBackpointers.containsKey(fieldTypeName)) break;
                    targetField = oneToOneBackpointers.get(fieldTypeName);
                    targetField.set(thisInstance, toPointTo);
                    success = true;
                }
                break;
            }
            return success;
        }catch (Exception e){
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private BackpointerType resolveBackpointerType(Field sourceField) {
        if(sourceField.isAnnotationPresent(ManyToOne.class)){
            return ONE_TO_MANY;
        }
        if(sourceField.isAnnotationPresent(OneToMany.class)){
            return MANY_TO_ONE;
        }
        if(sourceField.isAnnotationPresent(ManyToMany.class)){
            return MANY_TO_MANY;
        }
        if(sourceField.isAnnotationPresent(OneToOne.class)){
            return ONE_TO_ONE;
        }
        throw new RuntimeException("This code in EntityRelationshipsSyncronizer.resolveBackpointerType should never have been reached... Congratulations.");
    }

    private Class clazz;
    private CollectionsTypeResolver collectionsTypeResolver;

    private Map<String, Field> manyToOneBackpointers;
    private Map<String, Field> manyToManyBackpointers;
    private Map<String, Field> oneToManyBackpointers;
    private Map<String, Field> oneToOneBackpointers;

    public EntityRelationshipSyncronizer(Class clazz, CollectionsTypeResolver collectionsTypeResolver){

        this.clazz = clazz;
        this.collectionsTypeResolver = collectionsTypeResolver;

        manyToOneBackpointers = new HashMap<>();
        manyToManyBackpointers = new HashMap<>();
        oneToManyBackpointers = new HashMap<>();
        oneToOneBackpointers = new HashMap<>();

        populateBackpointerMaps();
    }

    private void populateBackpointerMaps() {
        val blackListedManyToOneBackpointers = new HashSet<String>();
        val blackListedManyToManyBackpointers = new HashSet<String>();
        val blackListedOneToManyBackpointers = new HashSet<String>();
        val blackListedOneToOneBackpointers = new HashSet<String>();
        for (Field field : clazz.getDeclaredFields()) {
            if(isIrrelevantField(field)) continue;
            if(field.isAnnotationPresent(ManyToOne.class))
                setBackpointerForAnnotationType(field, ManyToOne.class, blackListedManyToOneBackpointers, manyToOneBackpointers);
            else if(field.isAnnotationPresent(ManyToMany.class))
                setBackpointerForAnnotationType(field, ManyToMany.class, blackListedManyToManyBackpointers, manyToManyBackpointers);
            else if(field.isAnnotationPresent(OneToMany.class))
                setBackpointerForAnnotationType(field, OneToMany.class, blackListedOneToManyBackpointers, oneToManyBackpointers);
            else if(field.isAnnotationPresent(OneToOne.class))
                setBackpointerForAnnotationType(field, OneToOne.class, blackListedOneToOneBackpointers, oneToOneBackpointers);
        }
    }

    private boolean isIrrelevantField(Field field) {
        return
                Map.class.isAssignableFrom(field.getType()) ||
                !(field.isAnnotationPresent(AutoSynchronized.class) ||
                  field.getDeclaringClass().isAnnotationPresent(AutoSynchronized.class));
    }

    private void setBackpointerForAnnotationType(Field field, Class annotationType, Set<String> blacklist, Map<String, Field> backpointers){
        val typeName = resolveFieldTypeName(field);
        if(field.isAnnotationPresent(annotationType) && !blacklist.contains(typeName)){
            if(backpointers.containsKey(typeName)){
                blacklist.add(typeName);
                backpointers.remove(typeName);
            }else{
                field.setAccessible(true);
                backpointers.put(typeName, field);
            }
        }
    }

    private String resolveFieldTypeName(Field field) {
        val type = field.getType();
        if(!Collection.class.isAssignableFrom(type))
            return type.getSimpleName();
        return collectionsTypeResolver.resolveFor(clazz.getSimpleName() + "." + field.getName()).getSimpleName();
    }

    private Collection getOrInstantiateCollection(Field targetField, Object thisInstance) throws IllegalAccessException {
        val rawValue = targetField.get(thisInstance);
        return rawValue != null ? (Collection)rawValue : instantiateCollection(targetField, thisInstance);
    }

    private Collection instantiateCollection(Field field, Object thisInstance) throws IllegalAccessException {
        val collectionType = field.getType();
        Collection resultValue = null;
        if(Modifier.isInterface(collectionType.getModifiers())){
            if(collectionType.equals(Collection.class)) resultValue = new HashSet();
            else if(collectionType.equals(Set.class)) resultValue = new HashSet();
            else if(collectionType.equals(List.class)) resultValue = new ArrayList();
            else if(collectionType.equals(Queue.class)) resultValue = new LinkedList();
            else if(collectionType.equals(Deque.class)) resultValue = new ArrayDeque();
        }
        else
            resultValue = (Collection)genDefaultInstance(collectionType);
        field.set(thisInstance, resultValue);
        return (Collection) field.get(thisInstance);
    }
}

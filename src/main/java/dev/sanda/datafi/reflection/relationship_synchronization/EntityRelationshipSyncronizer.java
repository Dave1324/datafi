package dev.sanda.datafi.reflection.relationship_synchronization;

import static dev.sanda.datafi.DatafiStaticUtils.toPascalCase;
import static dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo.genDefaultInstance;
import static dev.sanda.datafi.reflection.relationship_synchronization.BackpointerType.*;

import dev.sanda.datafi.annotations.attributes.AutoSynchronized;
import dev.sanda.datafi.reflection.runtime_services.CollectionsTypeResolver;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
@SuppressWarnings("unchecked")
public class EntityRelationshipSyncronizer {

  public boolean trySetBackpointer(
    Field sourceField,
    Object thisInstance,
    Object toPointTo
  ) {
    try {
      val backpointerType = resolveBackpointerType(sourceField);
      boolean success = false;
      Field targetField;
      String fieldTypeName = toPointTo.getClass().getSimpleName();
      switch (Objects.requireNonNull(backpointerType)) {
        case ONE_TO_MANY:
          {
            if (!oneToManyBackpointers.containsKey(fieldTypeName)) break;
            targetField =
              getTargetField(sourceField, fieldTypeName, oneToManyBackpointers);
            val targetFieldValue = getOrInstantiateCollection(
              targetField,
              thisInstance
            );
            targetFieldValue.add(toPointTo);
            success = true;
          }
          break;
        case MANY_TO_MANY:
          {
            if (!manyToManyBackpointers.containsKey(fieldTypeName)) break;
            targetField =
              getTargetField(
                sourceField,
                fieldTypeName,
                manyToManyBackpointers
              );
            val targetFieldValue = getOrInstantiateCollection(
              targetField,
              thisInstance
            );
            targetFieldValue.add(toPointTo);
            success = true;
          }
          break;
        case MANY_TO_ONE:
          {
            if (!manyToOneBackpointers.containsKey(fieldTypeName)) break;
            targetField =
              getTargetField(sourceField, fieldTypeName, manyToOneBackpointers);
            targetField.set(thisInstance, toPointTo);
            success = true;
          }
        case ONE_TO_ONE:
          {
            if (!oneToOneBackpointers.containsKey(fieldTypeName)) break;
            targetField =
              getTargetField(sourceField, fieldTypeName, oneToOneBackpointers);
            targetField.set(thisInstance, toPointTo);
            success = true;
          }
          break;
      }
      return success;
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private BackpointerType resolveBackpointerType(Field sourceField) {
    if (sourceField.isAnnotationPresent(ManyToOne.class)) {
      return ONE_TO_MANY;
    }
    if (sourceField.isAnnotationPresent(OneToMany.class)) {
      return MANY_TO_ONE;
    }
    if (sourceField.isAnnotationPresent(ManyToMany.class)) {
      return MANY_TO_MANY;
    }
    if (sourceField.isAnnotationPresent(OneToOne.class)) {
      return ONE_TO_ONE;
    }
    throw new RuntimeException(
      "This code in EntityRelationshipsSyncronizer.resolveBackpointerType should never have been reached... Congratulations."
    );
  }

  private Class clazz;
  private Class apiSpec;
  private CollectionsTypeResolver collectionsTypeResolver;

  private Map<String, Map<String, Field>> manyToOneBackpointers;
  private Map<String, Map<String, Field>> manyToManyBackpointers;
  private Map<String, Map<String, Field>> oneToManyBackpointers;
  private Map<String, Map<String, Field>> oneToOneBackpointers;

  Map<String, Method> apiSpecGettersByFieldName;

  public EntityRelationshipSyncronizer(
    Class clazz,
    Class<?> apiSpec,
    CollectionsTypeResolver collectionsTypeResolver
  ) {
    this.clazz = clazz;
    this.apiSpec = apiSpec;
    this.collectionsTypeResolver = collectionsTypeResolver;
    manyToOneBackpointers = new HashMap<>();
    manyToManyBackpointers = new HashMap<>();
    oneToManyBackpointers = new HashMap<>();
    oneToOneBackpointers = new HashMap<>();
    apiSpecGettersByFieldName = new HashMap<>();

    populateBackpointerMaps();
  }

  private void populateBackpointerMaps() {
    val blackListedManyToOneBackpointers = new HashSet<String>();
    val blackListedManyToManyBackpointers = new HashSet<String>();
    val blackListedOneToManyBackpointers = new HashSet<String>();
    val blackListedOneToOneBackpointers = new HashSet<String>();
    for (val entry : getFieldsMap().entrySet()) {
      Field field = entry.getKey();
      if (
        field.isAnnotationPresent(ManyToOne.class)
      ) setBackpointerForAnnotationType(
        field,
        entry.getValue(),
        ManyToOne.class,
        blackListedManyToOneBackpointers,
        manyToOneBackpointers
      ); else if (
        field.isAnnotationPresent(ManyToMany.class)
      ) setBackpointerForAnnotationType(
        field,
        entry.getValue(),
        ManyToMany.class,
        blackListedManyToManyBackpointers,
        manyToManyBackpointers
      ); else if (
        field.isAnnotationPresent(OneToMany.class)
      ) setBackpointerForAnnotationType(
        field,
        entry.getValue(),
        OneToMany.class,
        blackListedOneToManyBackpointers,
        oneToManyBackpointers
      ); else if (
        field.isAnnotationPresent(OneToOne.class)
      ) setBackpointerForAnnotationType(
        field,
        entry.getValue(),
        OneToOne.class,
        blackListedOneToOneBackpointers,
        oneToOneBackpointers
      );
    }
  }

  private Map<Field, String> getFieldsMap() {
    Map<String, Field> fieldsByPascalCaseName = Arrays
      .stream(this.clazz.getDeclaredFields())
      .collect(
        Collectors.toMap(
          field -> toPascalCase(field.getName()),
          Function.identity()
        )
      );
    if (this.apiSpec == null) return Arrays
      .stream(this.clazz.getDeclaredFields())
      .filter(field -> !isIrrelevantField(field))
      .collect(
        Collectors.toMap(
          Function.identity(),
          field ->
            isExplicitlyScoped(field, null)
              ? field.getAnnotation(AutoSynchronized.class).referencedBy()
              : ""
        )
      ); else {
      apiSpecGettersByFieldName =
        Arrays
          .stream(this.apiSpec.getDeclaredMethods())
          .filter(
            method ->
              method.getName().startsWith("get") &&
              fieldsByPascalCaseName.containsKey(
                method.getName().replaceFirst("get", "")
              )
          )
          .collect(
            Collectors.toMap(
              method ->
                fieldsByPascalCaseName
                  .get(method.getName().replaceFirst("get", ""))
                  .getName(),
              Function.identity()
            )
          );
      return Arrays
        .stream(this.clazz.getDeclaredFields())
        .filter(
          field ->
            !isIrrelevantField(
              field,
              apiSpecGettersByFieldName.get(field.getName())
            )
        )
        .collect(
          Collectors.toMap(
            Function.identity(),
            field ->
              isExplicitlyScoped(
                  field,
                  apiSpecGettersByFieldName.get(field.getName())
                )
                ? getAnnotationInstance(
                  field,
                  apiSpecGettersByFieldName.get(field.getName())
                )
                  .referencedBy()
                : ""
          )
        );
    }
  }

  private boolean isIrrelevantField(Field field) {
    return isIrrelevantField(field, null);
  }

  private boolean isIrrelevantField(Field field, Method apiSpecGetter) {
    return (
      Map.class.isAssignableFrom(field.getType()) ||
      !(
        field.isAnnotationPresent(AutoSynchronized.class) ||
        field.getDeclaringClass().isAnnotationPresent(AutoSynchronized.class) ||
        (
          apiSpecGetter != null &&
          apiSpecGetter.isAnnotationPresent(AutoSynchronized.class) ||
          apiSpecGetter != null &&
          apiSpecGetter
            .getDeclaringClass()
            .isAnnotationPresent(AutoSynchronized.class)
        )
      )
    );
  }

  private void setBackpointerForAnnotationType(
    Field field,
    String toField,
    Class annotationType,
    Set<String> blacklist,
    Map<String, Map<String, Field>> backpointers
  ) {
    val typeName = resolveFieldTypeName(field);
    if (
      field.isAnnotationPresent(annotationType) && !blacklist.contains(typeName)
    ) {
      if (
        backpointers.containsKey(typeName) &&
        !isExplicitlyScoped(
          field,
          apiSpecGettersByFieldName.get(field.getName())
        )
      ) {
        throw new RuntimeException(
          "Entity of type " +
          clazz.getSimpleName() +
          " contains multiple instances of @AutoSynchronized" +
          " annotation on fields: \"" +
          field.getName() +
          "\" and \"" +
          backpointers
            .get(typeName)
            .entrySet()
            .iterator()
            .next()
            .getValue()
            .getName() +
          "\" for same referenced type \"" +
          typeName +
          "\"." +
          "If this is intentional, be sure to set the 'referencedBy' parameter on each annotation instance" +
          " so as to remove unresolvable ambiguity as to which field the @AutoSynchronized annotation instances" +
          " are each respectively intended to be referring to."
        );
      } else {
        field.setAccessible(true);
        if (!backpointers.containsKey(typeName)) backpointers.put(
          typeName,
          new HashMap<>()
        );
        backpointers.get(typeName).put(toField, field);
      }
    }
  }

  private boolean isExplicitlyScoped(Field field, Method apiSpecGetter) {
    val annotationInstance = getAnnotationInstance(field, apiSpecGetter);
    return (
      annotationInstance != null &&
      !annotationInstance.referencedBy().equals("")
    );
  }

  private AutoSynchronized getAnnotationInstance(
    Field field,
    Method apiSpecGetter
  ) {
    if (apiSpecGetter != null) {
      return apiSpecGetter.isAnnotationPresent(AutoSynchronized.class)
        ? apiSpecGetter.getAnnotation(AutoSynchronized.class)
        : field.getAnnotation(AutoSynchronized.class);
    }
    return field.getAnnotation(AutoSynchronized.class);
  }

  private Field getTargetField(
    Field sourceField,
    String fieldTypeName,
    Map<String, Map<String, Field>> backpointersMap
  ) {
    return backpointersMap.size() == 1
      ? backpointersMap
        .get(fieldTypeName)
        .entrySet()
        .iterator()
        .next()
        .getValue()
      : backpointersMap.get(fieldTypeName).get(sourceField.getName());
  }

  private String resolveFieldTypeName(Field field) {
    return resolveFieldType(field).getSimpleName();
  }

  private Class resolveFieldType(Field field) {
    val type = field.getType();
    if (!Collection.class.isAssignableFrom(type)) return type;
    return collectionsTypeResolver.resolveFor(
      clazz.getSimpleName() + "." + field.getName()
    );
  }

  private Collection getOrInstantiateCollection(
    Field targetField,
    Object thisInstance
  ) throws IllegalAccessException {
    val rawValue = targetField.get(thisInstance);
    return rawValue != null
      ? (Collection) rawValue
      : instantiateCollection(targetField, thisInstance);
  }

  private Collection instantiateCollection(Field field, Object thisInstance)
    throws IllegalAccessException {
    val collectionType = field.getType();
    Collection resultValue = null;
    if (Modifier.isInterface(collectionType.getModifiers())) {
      if (collectionType.equals(Collection.class)) resultValue =
        new HashSet(); else if (collectionType.equals(Set.class)) resultValue =
        new HashSet(); else if (collectionType.equals(List.class)) resultValue =
        new ArrayList(); else if (
        collectionType.equals(Queue.class)
      ) resultValue = new LinkedList(); else if (
        collectionType.equals(Deque.class)
      ) resultValue = new ArrayDeque();
    } else resultValue = (Collection) genDefaultInstance(collectionType);
    field.set(thisInstance, resultValue);
    return (Collection) field.get(thisInstance);
  }
}

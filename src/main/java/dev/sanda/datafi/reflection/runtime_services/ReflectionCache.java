package dev.sanda.datafi.reflection.runtime_services;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dev.sanda.datafi.annotations.EntityApiSpec;
import dev.sanda.datafi.code_generator.BasePackageResolver;
import dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo;
import dev.sanda.datafi.reflection.relationship_synchronization.EntityRelationshipSyncronizer;
import lombok.Getter;
import lombok.NonNull;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.sanda.datafi.DatafiStaticUtils.hasOneOfAnnotations;

@Component
public class ReflectionCache {

    private Reflections reflections;
    @Getter
    private Map<String, CachedEntityTypeInfo> entitiesCache;
    @Getter
    private Map<Map.Entry<String, Class<?>[]>, Method> resolversCache;

    @Autowired
    private CollectionsTypeResolver collectionsTypeResolver;

    @Autowired
    private BasePackageResolver basePackageResolver;

    @PostConstruct
    private void init() {
        reflections = new Reflections(basePackageResolver.getBasePackage());
        entitiesCache = new HashMap<>();
        resolversCache = new HashMap<>();
        Set<Class<?>> dataModelEntityTypes = getAnnotatedEntities();
        Map<Class<?>, Class<?>> dataModelEntityTypeApiSpecs = getAnnotatedEntityTypeApiSpecs();
        for (Class<?> currentType : dataModelEntityTypes) {
            if (isPersistableEntity(currentType))
                entitiesCache.put(
                        currentType.getSimpleName(),
                        new CachedEntityTypeInfo(
                                currentType,
                                getClassFields(currentType),
                                getPublicMethodsOf(currentType),
                                new EntityRelationshipSyncronizer(
                                        currentType,
                                        dataModelEntityTypeApiSpecs.get(currentType),
                                        collectionsTypeResolver))
                );
        }
    }

    private boolean isPersistableEntity(Class<?> currentType) {
        return currentType.isAnnotationPresent(Table.class) || currentType.isAnnotationPresent(Entity.class);
    }

    private Set<Class<?>> getAnnotatedEntities() {
        Set<Class<?>> entities = reflections.getTypesAnnotatedWith(Entity.class);
        entities.addAll(reflections.getTypesAnnotatedWith(Table.class));
        entities = Sets.newHashSet(entities);
        return entities;
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> getAnnotatedEntityTypeApiSpecs() {
        return  reflections.getTypesAnnotatedWith(EntityApiSpec.class)
                .stream()
                .filter(clazz -> hasOneOfAnnotations(clazz.getSuperclass(), Entity.class, Table.class))
                .collect(Collectors.toMap(Class::getSuperclass, Function.identity()));
    }

    private Collection<Method> getPublicMethodsOf(@NonNull Class<?> startClass) {
        List<Method> currentClassMethods = Lists.newArrayList(startClass.getMethods());
        Class<?> parentClass = startClass.getSuperclass();
        if (parentClass != null) {
            List<Method> parentClassFields =
                    (List<Method>) getPublicMethodsOf(parentClass);
            currentClassMethods.addAll(parentClassFields);
        }
        return currentClassMethods;
    }

    public static Collection<Field> getClassFields(@NonNull Class<?> startClass) {
        List<Field> currentClassFields = Lists.newArrayList(startClass.getDeclaredFields());
        Class<?> parentClass = startClass.getSuperclass();
        if (parentClass != null) {
            List<Field> parentClassFields =
                    (List<Field>) getClassFields(parentClass);
            currentClassFields.addAll(parentClassFields);
        }
        return currentClassFields;
    }

    public Object getIdOf(String clazzName, Object instance) {
        return entitiesCache.get(clazzName).getId(instance);
    }
}

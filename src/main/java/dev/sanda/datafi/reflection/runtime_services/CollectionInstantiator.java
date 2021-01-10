package dev.sanda.datafi.reflection.runtime_services;

import org.reflections.Reflections;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo.genDefaultInstance;

@Component
public class CollectionInstantiator {
    private final Reflections javaUtils = new Reflections("java.util");
    private final Map<Class<? extends Collection>, List<Class<? extends Collection>>> collectionTypes = new HashMap<>();
    private final Map<Map.Entry<Class<?>, Class<?>>, Class<?>> cache = new HashMap<>();

    @PostConstruct
    private void init(){
        Collection<Class<? extends Collection>> allCollectionTypes = javaUtils.getSubTypesOf(Collection.class);
        Collection<Class<? extends Collection>> collectionInterfaces =
                allCollectionTypes.stream().filter(Class::isInterface).collect(Collectors.toList());
        Collection<Class<? extends Collection>> collectionImplementations =
                allCollectionTypes.stream().filter(type -> !type.isInterface()).collect(Collectors.toList());
        for(Class<? extends Collection> collectionInterface : collectionInterfaces){
            collectionTypes.put(collectionInterface, new ArrayList<>());
            for(Class<? extends Collection> collectionImplementation : collectionImplementations)
                if (collectionInterface.isAssignableFrom(collectionImplementation))
                    collectionTypes.get(collectionInterface).add(collectionImplementation);
        }
    }

    public Collection instantiateCollection(Class<?> collectionType){
        if(Modifier.isInterface(collectionType.getModifiers())){
            if(collectionType.equals(Collection.class)) return new HashSet();
            if(collectionType.equals(Set.class)) return new HashSet();
            if(collectionType.equals(List.class)) return new ArrayList();
            if(collectionType.equals(Queue.class)) return new LinkedList();
            if(collectionType.equals(Deque.class)) return new ArrayDeque();
        }
        return (Collection)genDefaultInstance(collectionType);
    }
}

package dev.sanda.datafi.generator;

import com.squareup.javapoet.MethodSpec;
import dev.sanda.datafi.annotations.resolvers.GetAllBy;
import dev.sanda.datafi.annotations.resolvers.GetBy;
import dev.sanda.datafi.annotations.resolvers.GetByUnique;
import dev.sanda.datafi.annotations.WithResolver;

import javax.lang.model.element.*;
import java.util.*;

public class CustomResolversFactory {
    public static void resolveCustomResolvers(Set<? extends TypeElement> entities, Map<TypeElement, List<VariableElement>> annotatedFieldsMap, Map<TypeElement, List<MethodSpec>> customResolversMap) {
        for (TypeElement entity : entities) {
            //check for @GetBy and / or @GetAllBy field level annotations
            List<VariableElement> annotatedFields = getAnnotatedFieldsOf(entity);
            WithResolver[] customResolvers = getResolvers(entity);
            if(customResolvers != null){
                List<MethodSpec> customResolversImpl = new ArrayList<>();
                for (int i = 0; i < customResolvers.length; i++) {
                    customResolversImpl.add(SqlQueryMethodParser.parseResolver(customResolvers[i], entity));
                }
                customResolversMap.put(entity, customResolversImpl);
            }
            //as such, store a map of which entities need which extra endpoints
            if (!annotatedFields.isEmpty()) annotatedFieldsMap.put(entity, annotatedFields);
        }
    }

    /**
     * Compile and return a list of all fields within a given entity
     * which are annotated with '@GetBy' or '@GetAllBy'
     * @param entity
     * @return
     */
    public static List<VariableElement> getAnnotatedFieldsOf(TypeElement entity) {
        List<VariableElement> annotatedFields = new ArrayList<>();
        List<? extends Element> enclosedElements = entity.getEnclosedElements();
        for (Element enclosedElement : enclosedElements)
            if (isAnnotatedField(enclosedElement)) annotatedFields.add((VariableElement) enclosedElement);
        return annotatedFields;
    }

    private static WithResolver[] getResolvers(TypeElement entity) {
        final WithResolver[] annotationsByType = entity.getAnnotationsByType(WithResolver.class);
        if(annotationsByType == null) return null;
        List<WithResolver> resolvers = Arrays.asList(annotationsByType);
        for(AnnotationMirror annotationType : entity.getAnnotationMirrors()){
            if(annotationType.getAnnotationType().getAnnotation(WithResolver.class) != null){
                resolvers.add(annotationType.getAnnotationType().getAnnotation(WithResolver.class));
            }
        }
        return (WithResolver[]) resolvers.toArray();
    }

    /**
     * checks if a given field is annotated with '@GetBy' or '@GetAllBy'
     * @param enclosedElement
     * @return
     */
    private static boolean isAnnotatedField(Element enclosedElement) {
        return enclosedElement.getKind() == ElementKind.FIELD &&
                (enclosedElement.getAnnotation(GetBy.class) != null ||
                        enclosedElement.getAnnotation(GetAllBy.class) != null) ||
                enclosedElement.getAnnotation(GetByUnique.class) != null;
    }
}

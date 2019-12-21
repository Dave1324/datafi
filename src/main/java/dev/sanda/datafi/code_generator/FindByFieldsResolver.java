package dev.sanda.datafi.code_generator;

import dev.sanda.datafi.annotations.finders.FindAllBy;
import dev.sanda.datafi.annotations.finders.FindBy;
import dev.sanda.datafi.annotations.finders.FindByUnique;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;

@RequiredArgsConstructor
public class FindByFieldsResolver {
    @NonNull
    private ProcessingEnvironment processingEnv;

    public Map<TypeElement, List<VariableElement>> annotatedFieldsMap(Set<? extends TypeElement> entities) {
        Map<TypeElement, List<VariableElement>> result = new HashMap<>();
        for(TypeElement entity : entities){
            List<VariableElement> annotatedFields = new ArrayList<>();
            entity
                    .getEnclosedElements().stream().filter(e -> e.getKind().isField())
                    .forEach(field -> {
                        if(hasFindByAnnotation(field)){
                            annotatedFields.add((VariableElement) field);
                        }
                    });
            if(!annotatedFields.isEmpty())
                result.put(entity, annotatedFields);
        }
        return result;
    }

    private boolean hasFindByAnnotation(Element field) {
        return
                field.getAnnotation(FindBy.class) != null ||
                field.getAnnotation(FindAllBy.class) != null ||
                field.getAnnotation(FindByUnique.class) != null;
    }
}

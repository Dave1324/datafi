package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.annotations.free_text_search.WithFreeTextSearchByFields;
import lombok.Data;
import lombok.NonNull;
import lombok.val;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

import static com.squareup.javapoet.ParameterizedTypeName.get;
import static dev.sanda.datafi.DatafiStaticUtils.isArchivable;

@Data
public class FreeTextSearchMethodsFactory {

    @NonNull
    private ProcessingEnvironment processingEnv;
    private Map<TypeMirror, TypeElement> typeMirrorTypeElementMap;

    protected Map<TypeElement, MethodSpec> resolveFreeTextSearchMethods(Set<? extends TypeElement> entities) {
        Map<TypeElement, MethodSpec> result = new HashMap<>();
        typeMirrorTypeElementMap = entities.stream().collect(Collectors.toMap(Element::asType, entity -> entity));
        entities
        .stream()
        .filter(entity -> entity.getAnnotation(WithFreeTextSearchByFields.class) != null)
        .collect(Collectors.toSet())
        .forEach(entityWithFreeTextSearchFields -> {
            val searchFieldNames =
                    Arrays.asList(entityWithFreeTextSearchFields.getAnnotation(WithFreeTextSearchByFields.class).value());
            if(!searchFieldNames.isEmpty()){
                MethodSpec freeTextSearchMethod =
                        generateFreeTextSearchMethod(entityWithFreeTextSearchFields, searchFieldNames);
                result.put(entityWithFreeTextSearchFields, freeTextSearchMethod);
            }
        });
        return result;
    }

    private MethodSpec generateFreeTextSearchMethod(TypeElement entity, List<String> searchFieldNames) {
        String entityName = entity.getSimpleName().toString();
        String methodName = "freeTextSearch";
        ParameterSpec argument = ParameterSpec.builder(String.class, "searchTerm")
                .addAnnotation(AnnotationSpec.builder(Param.class)
                        .addMember("value", "$S", "searchTerm")
                        .build())
                .build();
        String freeTextSearchQuery = freeTextSearchQuery(entityName, searchFieldNames, isArchivable(entity, processingEnv), false);
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .addParameter(argument)
                .addParameter(Pageable.class, "paginator")
                .addAnnotation(AnnotationSpec.builder(Query.class)
                        .addMember("value", "$S", freeTextSearchQuery)
                        .build())
                .returns(get(ClassName.get(Page.class), ClassName.get(entity)))
                .build();
    }

    public static String freeTextSearchQuery(String entityName, List<String> searchFieldNames, boolean isArchivable, boolean selectCount) {
        String placeHolder = DatafiStaticUtils.firstLowerCaseLetterOf(entityName);
        String selectionPrefix =
                selectCount ? "SELECT COUNT(" + placeHolder + ") FROM " + entityName + " " + placeHolder
                : "SELECT " + placeHolder + " FROM " + entityName + " " + placeHolder;
        StringBuilder result = new StringBuilder(selectionPrefix);
        boolean isFirst = true;
        for (String fieldName : searchFieldNames) {
            final String conditionPrefix = isFirst ? " WHERE" : " OR";
            isFirst = false;
            val condition = " lower(" + placeHolder + "." + fieldName + ") " +
                    "LIKE lower(concat('%', :searchTerm, '%'))";
            result.append(conditionPrefix);
            result.append(condition);
        }
        if(isArchivable)
            result.append(" AND ").append(placeHolder).append(".isArchived = false");
        return result.toString();
    }
}

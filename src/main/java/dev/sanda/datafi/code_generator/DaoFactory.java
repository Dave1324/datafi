package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.annotations.finders.FindAllBy;
import dev.sanda.datafi.annotations.finders.FindBy;
import dev.sanda.datafi.annotations.finders.FindByUnique;
import dev.sanda.datafi.persistence.GenericDao;
import lombok.Data;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.Column;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.squareup.javapoet.ParameterizedTypeName.get;
import static dev.sanda.datafi.DatafiStaticUtils.isDirectlyOrIndirectlyAnnotatedAs;

@Data
public class DaoFactory {
    @NonNull
    private ProcessingEnvironment processingEnv;
    /**
     * generate the actual '<entity name>Dao.java' jpa repository for a given entity
     * @param entity - the given data model entity / table
     * @param annotatedFieldsMap - a reference telling us whether this repository needs any custom
     * @param customSQLQueriesMap
     */
    protected void generateDao(
            TypeElement entity,
            Map<TypeElement, List<VariableElement>> annotatedFieldsMap,
            Map<TypeElement, List<MethodSpec>> customSQLQueriesMap,
            Map<TypeElement, MethodSpec> freeTextSearchMethods) {

        String className = entity.getQualifiedName().toString();
        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        String simpleClassName = className.substring(lastDot + 1);
        String repositoryName = simpleClassName + "Dao";

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(repositoryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Repository.class)
                .addSuperinterface(get(ClassName.get(GenericDao.class), DatafiStaticUtils.getIdType(entity, processingEnv), ClassName.get(entity)));
        Collection<VariableElement> annotatedFields = annotatedFieldsMap.get(entity);
        if(annotatedFields != null)
            annotatedFields.forEach(annotatedField -> handleAnnotatedField(entity, builder, annotatedField));
        if(customSQLQueriesMap.get(entity) != null)
            customSQLQueriesMap.get(entity).forEach(builder::addMethod);
        if(freeTextSearchMethods.get(entity) != null)
            builder.addMethod(freeTextSearchMethods.get(entity));
        DatafiStaticUtils.writeToJavaFile(entity.getSimpleName().toString(), packageName, builder, processingEnv, "JpaRepository");
    }
    private void handleAnnotatedField(TypeElement entity, TypeSpec.Builder builder, VariableElement annotatedField) {
        if(isFindBy(annotatedField))
            handleFindBy(entity, builder, annotatedField);
        if(isFindAllBy(annotatedField))
            handleFindAllBy(entity, builder, annotatedField);
        if(isFindByUnique(annotatedField))
            handleFindByUnique(entity, builder, annotatedField);
    }

    private boolean isFindByUnique(VariableElement annotatedField) {
        return isDirectlyOrIndirectlyAnnotatedAs(annotatedField, FindByUnique.class);
    }

    private boolean isFindAllBy(VariableElement annotatedField) {
        return isDirectlyOrIndirectlyAnnotatedAs(annotatedField, FindAllBy.class);
    }

    private boolean isFindBy(VariableElement annotatedField) {
        return isDirectlyOrIndirectlyAnnotatedAs(annotatedField, FindBy.class);
    }

    private void handleFindByUnique(TypeElement entity, TypeSpec.Builder builder, VariableElement annotatedField) {
        if(isFindBy(annotatedField)){
            DatafiStaticUtils.logCompilationError(processingEnv, annotatedField, "@FindBy and @FindByUnique cannot by definition be used together");
        }else if(annotatedField.getAnnotation(Column.class) == null || !annotatedField.getAnnotation(Column.class).unique()){
            DatafiStaticUtils.logCompilationError(processingEnv, annotatedField, "In order to use @FindByUnique on a field, annotate the field as @Column(unique = true)");
        }
        else {
            builder
                    .addMethod(MethodSpec
                            .methodBuilder(
                                    "findBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName().toString()))
                            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                            .addParameter(
                                    ClassName.get(annotatedField.asType()),
                                    annotatedField.getSimpleName().toString())
                            .returns(get(ClassName.get(Optional.class), ClassName.get(entity)))
                            .build());
        }
    }

    private void handleFindAllBy(TypeElement entity, TypeSpec.Builder builder, VariableElement annotatedField) {
        builder
                .addMethod(MethodSpec
                        .methodBuilder(
                                "findAllBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName().toString()) + "In")
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(
                                get(ClassName.get(List.class), ClassName.get(annotatedField.asType())),
                                DatafiStaticUtils.toPlural(annotatedField.getSimpleName().toString()))
                        .returns(get(ClassName.get(List.class), ClassName.get(entity)))
                        .build());
    }

    private void handleFindBy(TypeElement entity, TypeSpec.Builder builder, VariableElement annotatedField) {
        builder
                .addMethod(MethodSpec
                        .methodBuilder(
                                "findBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName().toString()))
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(
                                ClassName.get(annotatedField.asType()),
                                annotatedField.getSimpleName().toString())
                        .returns(get(ClassName.get(List.class), ClassName.get(entity)))
                        .build());
    }
}

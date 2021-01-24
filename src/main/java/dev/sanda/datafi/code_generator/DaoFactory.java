package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.annotations.finders.FindAllBy;
import dev.sanda.datafi.annotations.finders.FindBy;
import dev.sanda.datafi.annotations.finders.FindByUnique;
import dev.sanda.datafi.code_generator.annotated_element_specs.EntityDalSpec;
import dev.sanda.datafi.code_generator.annotated_element_specs.FieldDalSpec;
import dev.sanda.datafi.persistence.GenericDao;
import lombok.Data;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Id;
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

    protected void generateDao(
            EntityDalSpec entityDalSpec,
            Map<TypeElement, List<MethodSpec>> customSQLQueriesMap,
            Map<TypeElement, MethodSpec> freeTextSearchMethods) {

        String className = entityDalSpec.getElement().getQualifiedName().toString();
        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        String simpleClassName = className.substring(lastDot + 1);
        String repositoryName = simpleClassName + "Dao";

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(repositoryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Repository.class)
                .addSuperinterface(
                        get(ClassName.get(GenericDao.class),
                        DatafiStaticUtils.getIdType(entityDalSpec.getElement(), processingEnv),
                        ClassName.get(entityDalSpec.getElement()))
                );
        Collection<FieldDalSpec> annotatedFields = entityDalSpec.getFieldDalSpecs();
        if (annotatedFields != null)
            annotatedFields.forEach(annotatedField -> handleAnnotatedField(entityDalSpec, builder, annotatedField));
        if (customSQLQueriesMap.get(entityDalSpec.getElement()) != null)
            customSQLQueriesMap.get(entityDalSpec.getElement()).forEach(builder::addMethod);
        if (freeTextSearchMethods.get(entityDalSpec.getElement()) != null)
            builder.addMethod(freeTextSearchMethods.get(entityDalSpec.getElement()));
        DatafiStaticUtils.writeToJavaFile(entityDalSpec.getSimpleName(), packageName, builder, processingEnv, "JpaRepository");
    }

    private void handleAnnotatedField(EntityDalSpec entityDalSpec, TypeSpec.Builder builder, FieldDalSpec fieldDalSpec) {
        if (isFindBy(fieldDalSpec))
            handleFindBy(entityDalSpec, builder, fieldDalSpec);
        if (isFindAllBy(fieldDalSpec))
            handleFindAllBy(entityDalSpec, builder, fieldDalSpec);
        if (isFindByUnique(fieldDalSpec))
            handleFindByUnique(entityDalSpec, builder, fieldDalSpec);
    }

    private boolean isFindByUnique(FieldDalSpec annotatedField) {
        return isDirectlyOrIndirectlyAnnotatedAs(annotatedField.getElement(), FindByUnique.class) ||
               annotatedField.hasAnnotation(FindByUnique.class);
    }

    private boolean isFindAllBy(FieldDalSpec annotatedField) {
        return isDirectlyOrIndirectlyAnnotatedAs(annotatedField.getElement(), FindAllBy.class) ||
               annotatedField.hasAnnotation(FindAllBy.class);
    }

    private boolean isFindBy(FieldDalSpec annotatedField) {
        return  isDirectlyOrIndirectlyAnnotatedAs(annotatedField.getElement(), FindBy.class) ||
                annotatedField.hasAnnotation(FindBy.class);
    }

    private void handleFindByUnique(EntityDalSpec entityDalSpec, TypeSpec.Builder builder, FieldDalSpec annotatedField) {
        if (isFindBy(annotatedField)) {
            DatafiStaticUtils.logCompilationError(processingEnv, annotatedField.getElement(), "@FindBy and @FindByUnique cannot by definition be used together");
        } else if (!isUniqueColumn(annotatedField)) {
            DatafiStaticUtils.logCompilationError(processingEnv, annotatedField.getElement(), "In order to use @FindByUnique on a field, annotate the field as @Column(unique = true)");
        } else {
            builder
                    .addMethod(MethodSpec
                            .methodBuilder(
                                    "findBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName().toString()))
                            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                            .addParameter(
                                    ClassName.get(annotatedField.getElement().asType()),
                                    annotatedField.getSimpleName().toString())
                            .returns(get(ClassName.get(Optional.class), ClassName.get(entityDalSpec.getElement())))
                            .build());
        }
    }

    private boolean isUniqueColumn(FieldDalSpec annotatedField) {
        return  (annotatedField.hasAnnotation(Column.class) && annotatedField.getAnnotation(Column.class).unique()) ||
                annotatedField.hasAnnotation(Id.class) || annotatedField.hasAnnotation(EmbeddedId.class);
    }

    private void handleFindAllBy(EntityDalSpec entityDalSpec, TypeSpec.Builder builder, FieldDalSpec annotatedField) {
        builder
                .addMethod(MethodSpec
                        .methodBuilder(
                                "findAllBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName()) + "In")
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(
                                get(ClassName.get(List.class), ClassName.get(annotatedField.getElement().asType())),
                                DatafiStaticUtils.toPlural(annotatedField.getSimpleName()))
                        .returns(get(ClassName.get(List.class), ClassName.get(entityDalSpec.getElement())))
                        .build());
    }

    private void handleFindBy(EntityDalSpec entityDalSpec, TypeSpec.Builder builder, FieldDalSpec annotatedField) {
        builder
                .addMethod(MethodSpec
                        .methodBuilder(
                                "findBy" + DatafiStaticUtils.toPascalCase(annotatedField.getSimpleName()))
                        .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                        .addParameter(
                                ClassName.get(annotatedField.getElement().asType()),
                                annotatedField.getSimpleName())
                        .returns(get(ClassName.get(List.class), ClassName.get(entityDalSpec.getElement())))
                        .build());
    }
}

package dev.sanda.datafi.code_generator;


import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.code_generator.query.CustomSQLQueryFactory;
import dev.sanda.datafi.reflection.runtime_services.CollectionsTypeResolver;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.sanda.datafi.DatafiStaticUtils.*;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Takes care of generating all the source files needed for a jpa data access layer.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Set<? extends TypeElement> entities = getPersistableEntities(roundEnvironment);
        if (entities.isEmpty()) return false;
        val customSqlQueriesMap = new CustomSQLQueryFactory(processingEnv).constructCustomQueries(entities);
        val searchMethodsMap = new FreeTextSearchMethodsFactory(processingEnv).resolveFreeTextSearchMethods(entities);
        Map<TypeElement, List<VariableElement>> annotatedFieldsMap =
                new FindByFieldsResolver(processingEnv).annotatedFieldsMap(entities);
        //generate a custom jpa repository for each entity
        DaoFactory daoFactory = new DaoFactory(processingEnv);
        DataManagerFactory dataManagerFactory = new DataManagerFactory(processingEnv, DatafiStaticUtils.getBasePackage(roundEnvironment));
        entities.forEach(entity -> {
            daoFactory.generateDao(entity, annotatedFieldsMap, customSqlQueriesMap, searchMethodsMap);
            dataManagerFactory.addDataManager(entity);
        });
        dataManagerFactory.addBasePackageResolver();
        dataManagerFactory.writeToFile();
        /*
        create a configuration source file such that
        generated spring beans are included within
        the runtime target application context
        */
        setComponentScan(entities);
        setEntityFieldCollectionTypeResolversBean(entities, roundEnvironment);
        //return false - these annotations are needed for the web-service layer as well
        return false;

    }

    private void setEntityFieldCollectionTypeResolversBean(Set<? extends TypeElement> entities, RoundEnvironment env) {
        Map<String, ClassName> collectionsTypes = new HashMap<>();
        entities.forEach(
                entity -> getFieldsOf(entity).stream()
                        .filter(field -> isCollectionField(field, processingEnv))
                        .forEach(collectionField -> {
                            String key =
                                    entity.getSimpleName().toString() + "." + collectionField.getSimpleName().toString();
                            ClassName value = ClassName.bestGuess(
                                    collectionField
                                            .asType()
                                            .toString()
                                            .replaceAll(".+<", "")
                                            .replaceAll(">", "")
                            );
                            collectionsTypes.put(key, value);
                        })
        );
        generateCollectionsTypesResolver(collectionsTypes, env);
    }
    private void generateCollectionsTypesResolver(Map<String, ClassName> collectionsTypes, RoundEnvironment env) {
        TypeName mapType = ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(String.class), ClassName.get(Class.class));
        val builder = MethodSpec.methodBuilder("collectionsTypesResolver")
                .addAnnotation(Bean.class)
                .addModifiers(PUBLIC)
                .returns(ClassName.get(CollectionsTypeResolver.class));
        builder.addStatement("$T typeResolverMap = new $T()", mapType, mapType);
        collectionsTypes.forEach((key, type) -> builder.addStatement("typeResolverMap.put($S, $T.class)", key, type));
        builder.addStatement("return new $T(typeResolverMap)", CollectionsTypeResolver.class);
        TypeSpec typeResolverMapFactory = TypeSpec.classBuilder("TypeResolverMapFactory")
                .addModifiers(PUBLIC)
                .addAnnotation(Configuration.class)
                .addMethod(builder.build())
                .build();
        try {
            val file = JavaFile.builder(getBasePackage(env), typeResolverMapFactory).build();
            file.writeTo(System.out);
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    /**
     * compile and return a list of all entities annotated
     * with @Entity or @Table, and as such
     * relevant to the code generator
     *
     * @param roundEnvironment
     * @return
     */
    private Set<? extends TypeElement> getPersistableEntities(RoundEnvironment roundEnvironment) {
        return DatafiStaticUtils.getEntitiesSet(roundEnvironment);
    }

    private void setComponentScan(Set<? extends TypeElement> entities) {
        if (!entities.isEmpty()) {
            String className = entities.iterator().next().getQualifiedName().toString();
            int lastdot = className.lastIndexOf('.');
            String basePackageName = className.substring(0, lastdot);
            String simpleClassName = "SandaClasspathConfiguration";
            TypeSpec.Builder builder = TypeSpec.classBuilder(simpleClassName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Configuration.class)
                    .addAnnotation(AnnotationSpec.builder(ComponentScan.class)
                            .addMember(
                                    "basePackages",
                                    "{$S}",
                                    "dev.sanda")
                            .build());
            writeToJavaFile(simpleClassName, basePackageName, builder, processingEnv, "configuration source file");
        }
    }
}

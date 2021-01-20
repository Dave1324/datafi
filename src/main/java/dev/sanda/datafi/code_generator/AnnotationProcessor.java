package dev.sanda.datafi.code_generator;


import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.code_generator.annotated_element_specs.EntityDalSpec;
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
        val entitySpecs = getEntityApiSpecs(roundEnvironment, processingEnv);
        if (entitySpecs.isEmpty()) return false;
        val customSqlQueriesMap = new CustomSQLQueryFactory(processingEnv).constructCustomQueries(entitySpecs);
        val searchMethodsMap = new FreeTextSearchMethodsFactory(processingEnv).resolveFreeTextSearchMethods(entitySpecs);
        //generate a custom jpa repository for each entity
        DaoFactory daoFactory = new DaoFactory(processingEnv);
        DataManagerFactory dataManagerFactory = new DataManagerFactory(processingEnv, DatafiStaticUtils.getBasePackage(roundEnvironment));
        entitySpecs.forEach(entityDalSpec -> {
            daoFactory.generateDao(entityDalSpec, customSqlQueriesMap, searchMethodsMap);
            dataManagerFactory.addDataManager(entityDalSpec);
        });
        dataManagerFactory.addBasePackageResolver();
        dataManagerFactory.writeToFile();
        /*
        create a configuration source file such that
        generated spring beans are included within
        the runtime target application context
        */
        setComponentScan(entitySpecs);
        setEntityFieldCollectionTypeResolversBean(entitySpecs, roundEnvironment);
        //return false - these annotations are needed for the web-service layer as well
        return false;

    }

    private void setEntityFieldCollectionTypeResolversBean(List<EntityDalSpec> entityDalSpecs, RoundEnvironment env) {
        Map<String, ClassName> collectionsTypes = new HashMap<>();
        entityDalSpecs.forEach(
                entity -> getFieldsOf(entity.getElement()).stream()
                        .filter(field -> isCollectionField(field, processingEnv))
                        .forEach(collectionField -> {
                            String key =
                                    entity.getSimpleName() + "." + collectionField.getSimpleName().toString();
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

    private void setComponentScan(List<EntityDalSpec> entityDalSpecs) {
        if (!entityDalSpecs.isEmpty()) {
            String className = entityDalSpecs.iterator().next().getElement().getQualifiedName().toString();
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

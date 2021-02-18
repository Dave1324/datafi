package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.code_generator.annotated_element_specs.EntityDalSpec;
import dev.sanda.datafi.service.DataManager;
import lombok.Data;
import lombok.NonNull;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import static dev.sanda.datafi.DatafiStaticUtils.writeToJavaFile;

@Data
public class DataManagerFactory {
    @NonNull
    private ProcessingEnvironment processingEnv;
    @NonNull
    private String basePackage;

    private TypeSpec.Builder dataManagersConfig = initDataManagerConfig();
    private final static ClassName dataManagerType = ClassName.get(DataManager.class);

    public void addDataManager(EntityDalSpec entityDalSpec) {
        final ClassName entityType = ClassName.get(entityDalSpec.getElement());
        MethodSpec.Builder builder =
                MethodSpec
                        .methodBuilder(DatafiStaticUtils.camelCaseNameOf(entityDalSpec.getElement()) + "DataManager")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Bean.class)
                        .returns(ParameterizedTypeName.get(dataManagerType, entityType))
                        .addStatement("return new $T($T.class)", dataManagerType, entityType);
        dataManagersConfig.addMethod(builder.build());
    }

    public void addBasePackageResolver(List<String> modelPackageNames) {
        val builder = MethodSpec.methodBuilder("basePackageResolver")
                .addAnnotation(Bean.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(BasePackageResolver.class)
                .addStatement("$T packageNames = new $T()",
                        ParameterizedTypeName.get(List.class, String.class),
                        ParameterizedTypeName.get(ArrayList.class, String.class));
        modelPackageNames.forEach(name -> builder.addStatement("packageNames.add($S)", name));
        dataManagersConfig.addMethod(builder
                .addStatement("return new $T($L)", BasePackageResolver.class, "packageNames")
                .build());
    }

    private static TypeSpec.Builder initDataManagerConfig() {
        return TypeSpec.classBuilder("DataManagersConfig")
                .addAnnotation(Configuration.class)
                .addMethod(MethodSpec.methodBuilder("nullTypeDataManager")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Bean.class)
                        .addAnnotation(Primary.class)
                        .returns(dataManagerType)
                        .addStatement("return new $T()", dataManagerType)
                        .build());
    }

    public void writeToFile() {
        writeToJavaFile(
                "DataManagersConfig",
                basePackage,
                dataManagersConfig,
                processingEnv,
                "Data manager beans");
    }
}

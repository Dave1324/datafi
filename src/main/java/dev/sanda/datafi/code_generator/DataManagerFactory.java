package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.service.DataManager;
import lombok.Data;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

@Data
public class DataManagerFactory {
    @NonNull
    private ProcessingEnvironment processingEnv;
    @NonNull
    private String basePackage;

    private TypeSpec.Builder dataManagersConfig = initDataManagerConfig();
    private final static ClassName dataManagerType = ClassName.get(DataManager.class);

    public void addDataManager(TypeElement entity){
        final ClassName entityType = ClassName.get(entity);
        MethodSpec.Builder builder =
                MethodSpec
                .methodBuilder(DatafiStaticUtils.camelCaseNameOf(entity) + "DataManager")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Bean.class)
                .returns(ParameterizedTypeName.get(dataManagerType, entityType))
                .addStatement("return new $T($T.class)", dataManagerType, entityType);
        dataManagersConfig.addMethod(builder.build());
    }

    public void addBasePackageResolver() {
        dataManagersConfig.addMethod(MethodSpec.methodBuilder("basePackageResolver")
                .addAnnotation(Bean.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(BasePackageResolver.class)
                .addStatement("return new $T($S)", BasePackageResolver.class, this.basePackage)
                .build());
    }

    private static TypeSpec.Builder initDataManagerConfig(){
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
    public void writeToFile(){
        DatafiStaticUtils.writeToJavaFile(
                "DataManagersConfig",
                basePackage,
                dataManagersConfig,
                processingEnv,
                "Data manager beans");
    }
}

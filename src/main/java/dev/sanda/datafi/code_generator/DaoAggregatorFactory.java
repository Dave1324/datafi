package dev.sanda.datafi.code_generator;

import com.squareup.javapoet.*;
import dev.sanda.datafi.persistence.GenericDao;
import dev.sanda.datafi.service.DaoCollector;
import lombok.Getter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Component;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.List;

import static dev.sanda.datafi.DatafiStaticUtils.writeToJavaFile;


public class DaoAggregatorFactory {

    public static void generateDaoCollectorImpl(List<String> packageNames, ProcessingEnvironment processingEnvironment){
        val componentScanBuilder = AnnotationSpec.builder(ComponentScan.class);
        val entityScanBuilder = AnnotationSpec.builder(EntityScan.class);
        val enableJpaReposBuilder = AnnotationSpec.builder(EnableJpaRepositories.class);
        val names = "{\"" + String.join("\", \"", packageNames) + "\"}";
        componentScanBuilder.addMember("value", "$L", names);
        entityScanBuilder.addMember("value", "$L", names);
        enableJpaReposBuilder.addMember("value", "$L", names);

        val builder = TypeSpec
                .classBuilder("DaoCollectorImpl")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(DaoCollector.class)
                .addAnnotation(Component.class)
                .addAnnotation(componentScanBuilder.build())
                .addAnnotation(entityScanBuilder.build())
                .addAnnotation(enableJpaReposBuilder.build())
                .addField(FieldSpec.builder(
                        ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(GenericDao.class)),
                        "daos",
                        Modifier.PRIVATE)
                        .addAnnotation(Getter.class)
                        .addAnnotation(Autowired.class)
                        .build()
                );
        writeToJavaFile(
                "DaoCollectorImpl",
                packageNames.get(0),
                builder,
                processingEnvironment,
                "Dao aggregator for data manager"
        );
    }
}

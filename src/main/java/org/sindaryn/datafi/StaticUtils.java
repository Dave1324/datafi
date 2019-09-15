package org.sindaryn.datafi;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.sindaryn.datafi.reflection.ReflectionCache;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StaticUtils {
    public static String toPascalCase(String string){
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }
    public static String toCamelCase(String string){
        return string.substring(0, 1).toLowerCase() + string.substring(1);
    }
    public static String toPlural(String aString){
        String suffix = "";
        if(aString.endsWith("s")) suffix = "es";
        else if(aString.endsWith("y")) {
            aString = aString.substring(0, aString.length() - 1);
            suffix = "ies";
        } else suffix  ="s";
        return aString + suffix;
    }

    public static void logCompilationError(ProcessingEnvironment processingEnvironment, Element element, String message) {
        processingEnvironment
                .getMessager()
                .printMessage(Diagnostic.Kind.ERROR,
                        message + " --> " + element.getSimpleName().toString(), element);
    }

    public static void writeToJavaFile(String entitySimpleName,
                                       String packageName,
                                       TypeSpec.Builder builder,
                                       ProcessingEnvironment processingEnvironment,
                                       String templateType) {
        builder.addJavadoc(
                entitySimpleName +
                        " " + templateType + " generated by org.sindaryn @" +
                        LocalDateTime.now());
        final TypeSpec newClass = builder.build();
        final JavaFile javaFile = JavaFile.builder(packageName, newClass).build();

        try {
            javaFile.writeTo(System.out);
            javaFile.writeTo(processingEnvironment.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static <T> Object getId(T input, ReflectionCache reflectionCache) {
        return reflectionCache.getEntitiesCache().get(input.getClass().getSimpleName()).invokeGetter(input, "id");
    }
    public static void throwEntityNotFoundException(String simpleName, Object id){
        throw new RuntimeException("Cannot find " + simpleName + " by id: " + id);
    }
    public static<T> List<Object> getIdList(Collection<T> input, ReflectionCache reflectionCache) {
        List<Object> ids = new ArrayList<>();
        input.forEach(item -> ids.add(getId(item, reflectionCache)));
        return ids;
    }
}

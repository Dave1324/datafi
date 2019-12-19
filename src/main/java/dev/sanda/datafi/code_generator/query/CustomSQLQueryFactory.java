package dev.sanda.datafi.code_generator.query;

import com.squareup.javapoet.*;
import dev.sanda.datafi.annotations.query.WithNativeQuery;
import dev.sanda.datafi.annotations.query.WithNativeQueryScripts;
import dev.sanda.datafi.annotations.query.WithQuery;
import dev.sanda.datafi.annotations.query.WithQueryScripts;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.FileCopyUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

import static com.google.common.collect.Maps.immutableEntry;
import static java.nio.charset.StandardCharsets.UTF_8;

@RequiredArgsConstructor
public class CustomSQLQueryFactory {

    @NonNull
    private ProcessingEnvironment env;
    @NonNull
    private Map<TypeElement, Map<String, TypeName>> entitiesFields;

    public Map<TypeElement, List<MethodSpec>> constructCustomQueries(Set<? extends TypeElement> entities) {
        Map<TypeElement, List<MethodSpec>> customQueriesMap = new HashMap<>();
        for (TypeElement entity : entities) {
            List<CustomSQLQuery> customQueries = getCustomSQLQueries(entity);
                List<MethodSpec> customQuerieMethodSpecs = new ArrayList<>();
                for (CustomSQLQuery query : customQueries) {
                    customQuerieMethodSpecs.add(generateCustomQueryMethod(query));
                }
                customQueriesMap.put(entity, customQuerieMethodSpecs);
        }
        return customQueriesMap;
    }

    private MethodSpec generateCustomQueryMethod(CustomSQLQuery query) {
        var queryAnnotationBuilder = AnnotationSpec.builder(Query.class)
                .addMember("value", "$S", query.getSql());
        if(query.isNative())
            queryAnnotationBuilder.addMember("nativeQuery", "$L", true);
        return   MethodSpec.methodBuilder(query.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(queryAnnotationBuilder.build())
                .addParameters(query.parameterSpecs())
                .returns(query.returnTypeName())
                .build();
    }

    private List<CustomSQLQuery> getCustomSQLQueries(TypeElement entity) {
        entitiesFields.put(entity, resolveFieldTypesOf(entity));
        final WithQuery[] individualQueries = entity.getAnnotationsByType(WithQuery.class);
        final WithNativeQuery[] individualNativeQueries = entity.getAnnotationsByType(WithNativeQuery.class);
        final WithQueryScripts queryScripts = entity.getAnnotation(WithQueryScripts.class);
        final WithNativeQueryScripts nativeQueryScripts = entity.getAnnotation(WithNativeQueryScripts.class);
        List<CustomSQLQuery> customSQLQueries = new ArrayList<>();

        if(individualQueries != null){
            for (WithQuery query : individualQueries) {
                customSQLQueries.add(parseQuery(query.name(), query.sql(), entity));
            }
        }

        if(individualNativeQueries != null){
            for (WithNativeQuery query : individualNativeQueries) {
                customSQLQueries.add(parseIndividualNativeQuery(query.name(), query.sql(), entity));
            }
        }

        if(queryScripts != null){
            for (String scriptPath : queryScripts.value()) {
                customSQLQueries.add(parseQueryScript(scriptPath, entity));
            }
        }

        if(nativeQueryScripts != null){
            for (String scriptPath : nativeQueryScripts.value()) {
                customSQLQueries.add(parseNativeQueryScript(scriptPath, entity));
            }
        }
        return customSQLQueries;
    }

    private CustomSQLQuery parseNativeQueryScript(String path, TypeElement entity) {
        CustomSQLQuery customSQLQuery = parseQueryScript(path, entity);
        customSQLQuery.setNative(true);
        return customSQLQuery;
    }

    private CustomSQLQuery parseQueryScript(String path, TypeElement entity) {
        path = path.endsWith(".sql") ? path : path + ".sql";
        //get file
        final ClassPathResource resource = new ClassPathResource("/sql/" + path);
        //validate and assign filename as query name
        String name = formatAndValidateName(Objects.requireNonNull(resource.getFilename()));
        //read sql string from file
        String sql = resourceToString(resource);
        return parseQuery(name, sql, entity);
    }

    private CustomSQLQuery parseIndividualNativeQuery(String name, String sql, TypeElement entity) {
        CustomSQLQuery customSQLQuery = parseQuery(name, sql, entity);
        customSQLQuery.setNative(true);
        return customSQLQuery;
    }

    private CustomSQLQuery parseQuery(String name, String sql, TypeElement entity) {
        CustomSQLQuery customSQLQuery = new CustomSQLQuery();
        customSQLQuery.setAnnotatedEntity(entity);
        customSQLQuery.setName(formatAndValidateName(name));
        String sqlString = parseSqlString(sql, customSQLQuery.getArgs(), entity);
        customSQLQuery.setSql(sqlString);
        customSQLQuery.setReturnSignature(determineSQLReturnSignature(sqlString));
        return customSQLQuery;
    }

    private QueryReturnSignature determineSQLReturnSignature(String sqlString) {
        final String sql = sqlString.toUpperCase();
        boolean isUnique =
                sql.endsWith("LIMIT 1") ||
                sql.substring(0, sql.indexOf(" ")).equals("INSERT") ||
                sql.substring(0, sql.indexOf(" ")).equals("REPLACE");
        if(isUnique)
            return QueryReturnSignature.SINGLE_RECORD;
        else
            return QueryReturnSignature.LIST_OF_RECORDS;
    }

    private String parseSqlString(String sql, Map<String, TypeName> args, TypeElement entity) {
        String lineSeparator = System.getProperty("line.separator");
        String formattedSqlString =
                sql
                .replaceAll(lineSeparator, " ")
                .replaceAll("\\s+", " ")
                .trim();
        String[] lexemes = formattedSqlString.split(" ");
        StringBuilder finalSql = new StringBuilder();
        Map.Entry<String, TypeName> arg;
        for (String lexeme : lexemes) {
            if((arg = parseLexemeForArg(lexeme, args, entitiesFields.get(entity))) != null) {
                args.putIfAbsent(arg.getKey(), arg.getValue());
                finalSql.append(" :").append(arg.getKey());
            }else{
                finalSql.append(" ").append(lexeme);
            }
        }
        return finalSql.toString().trim();
    }

    private Map.Entry<String, TypeName> parseLexemeForArg(
            String lexeme,
            Map<String, TypeName> argsSoFar,
            Map<String, TypeName> entityFields) {
        if(!lexeme.contains(":")) return null;
        if(lexeme.contains(":::")){
            compilationFailureWithMessage(
                    lexeme + " contains more than two colons, " +
                            "must be either two or one for valid arg syntax.", env);
        }
        final String argName = lexeme.substring(lexeme.lastIndexOf(":") + 1);
        TypeName argType;
        if(lexeme.contains("::")){
            if(argsSoFar.containsKey(argName)){
                compilationFailureWithMessage("sql argument name collision: " + argName, env);
            }
            String typeNameString = lexeme.substring(0, lexeme.indexOf(":"));
            argType = resolvePrimitiveType(typeNameString);
            if(argType == null){
                compilationFailureWithMessage(
                        "cannot resolve type " + typeNameString +
                                " for sql argument " + argName, env);
            }
        }else {
            argType = entityFields.get(argName);
            if(argType == null)
                argType = argsSoFar.get(argName);
            if(argType == null) {
                compilationFailureWithMessage(
                        lexeme + " is invalid syntax; must either " +
                                "specify type with double colon syntax or " +
                                "reference primitive field of annotated entity.", env);
            }
        }
        return immutableEntry(argName, argType);
    }

    private TypeName resolvePrimitiveType(String typeNameString) {
        Class<?> argClazz;
        boolean isList = typeNameString.endsWith("[]");
        if(isList) typeNameString = typeNameString.substring(0, typeNameString.indexOf("["));
        switch (typeNameString){
            case "byte": argClazz = byte.class; break;
            case "Byte": argClazz = Byte.class; break;
            case "short": argClazz = short.class; break;
            case "Short": argClazz = Short.class; break;
            case "int": argClazz = int.class; break;
            case "Integer": argClazz = Integer.class; break;
            case "long": argClazz = long.class; break;
            case "Long": argClazz = Long.class; break;
            case "float": argClazz = float.class; break;
            case "Float": argClazz = Float.class; break;
            case "double": argClazz = double.class; break;
            case "Double": argClazz = Double.class; break;
            case "boolean": argClazz = boolean.class; break;
            case "Boolean": argClazz = Boolean.class; break;
            case "char": argClazz = char.class; break;
            case "Character": argClazz = Character.class; break;
            case "String": argClazz = String.class; break;
            case "string": argClazz = String.class; break;
            default: return null;
        }
        if(isList)
            return ParameterizedTypeName.get(List.class, argClazz);
        else
            return TypeName.get(argClazz);
    }

    private String formatAndValidateName(String name) {
        String trimmedName = name.trim();
        if(!isValidJavaIdentifier(trimmedName)){
            compilationFailureWithMessage(invalidNameMessage(trimmedName), env);
        }
        return name;
    }

    private static void compilationFailureWithMessage(String message, ProcessingEnvironment env) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }

    public static boolean isValidJavaIdentifier(String s) {
        if (s.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String invalidNameMessage(String name){
        return name + " is not a valid java identifier. please verify that all names " +
                "given to custom sql queries are valid java identifiers.";
    }

    private static Map<String, TypeName> resolveFieldTypesOf(TypeElement typeElement) {
        Map<String, TypeName> result = new HashMap<>();
        for(Element field : typeElement.getEnclosedElements())
            if (field.getKind().isField())
                result.put(field.getSimpleName().toString(), ClassName.get(field.asType()));
        return result;
    }

    public String resourceToString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            compilationFailureWithMessage(e.toString(), env);
            return null;
        }
    }

}

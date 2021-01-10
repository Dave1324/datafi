package dev.sanda.datafi.code_generator.query;

import com.squareup.javapoet.*;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.annotations.query.WithNativeQuery;
import dev.sanda.datafi.annotations.query.WithNativeQueryScripts;
import dev.sanda.datafi.annotations.query.WithQuery;
import dev.sanda.datafi.annotations.query.WithQueryScripts;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.FileCopyUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

import static com.google.common.collect.Maps.immutableEntry;

@RequiredArgsConstructor
public class CustomSQLQueryFactory {

    @NonNull
    private ProcessingEnvironment env;

    private Map<TypeElement, Map<String, TypeName>> entitiesFields;

    public Map<TypeElement, List<MethodSpec>> constructCustomQueries(Set<? extends TypeElement> entities) {
        entitiesFields = DatafiStaticUtils.getEntitiesFieldsMap(entities);
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
        AnnotationSpec.Builder queryAnnotationBuilder = AnnotationSpec.builder(Query.class)
                .addMember("value", "$S", query.getSql());
        if (query.isNative())
            queryAnnotationBuilder.addMember("nativeQuery", "$L", true);
        return MethodSpec.methodBuilder(query.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(queryAnnotationBuilder.build())
                .addParameters(query.parameterSpecs())
                .returns(query.returnSignature())
                .build();
    }

    private List<CustomSQLQuery> getCustomSQLQueries(TypeElement entity) {
        entitiesFields.put(entity, resolveFieldTypesOf(entity));
        final WithQuery[] individualQueries = entity.getAnnotationsByType(WithQuery.class);
        final WithNativeQuery[] individualNativeQueries = entity.getAnnotationsByType(WithNativeQuery.class);
        final WithQueryScripts queryScripts = entity.getAnnotation(WithQueryScripts.class);
        final WithNativeQueryScripts nativeQueryScripts = entity.getAnnotation(WithNativeQueryScripts.class);
        List<CustomSQLQuery> customSQLQueries = new ArrayList<>();

        if (individualQueries != null) {
            for (WithQuery query : individualQueries) {
                customSQLQueries.add(parseQuery(query.name(), query.jpql(), entity));
            }
        }

        if (individualNativeQueries != null) {
            for (WithNativeQuery query : individualNativeQueries) {
                customSQLQueries.add(parseIndividualNativeQuery(query.name(), query.sql(), entity));
            }
        }

        if (queryScripts != null) {
            for (String scriptPath : queryScripts.value()) {
                customSQLQueries.add(parseQueryScript(scriptPath, entity));
            }
        }

        if (nativeQueryScripts != null) {
            for (String scriptPath : nativeQueryScripts.value()) {
                customSQLQueries.add(parseQueryScript(scriptPath, entity));
            }
        }
        return customSQLQueries;
    }

    private CustomSQLQuery parseQueryScript(String path, TypeElement entity) {
        //get file
        final ClassPathResource resource = new ClassPathResource(path);
        //validate and assign filename as query name
        String name = formatAndValidateName(Objects.requireNonNull(resource.getFilename()));
        //read sql string from file
        String sql = sqlResourceToString(resource.getPath());
        //set nativeQuery flag
        boolean nativeQuery = determineIfIsNativeQuery(path);
        //set isNativeQuery
        CustomSQLQuery result = parseQuery(name, sql, entity);
        result.setNative(nativeQuery);
        return result;
    }

    private boolean determineIfIsNativeQuery(String path) {
        if (path.endsWith(".sql")) return true;
        if (path.endsWith(".jpql")) return false;
        else {
            compilationFailureWithMessage(String.format("Invalid query script path: %s; Paths must end with either a .sql or a .jpql suffix", path), env);
            return false;
        }
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
        customSQLQuery.setReturnPlurality(determineSQLReturnSignature(sqlString));
        return customSQLQuery;
    }

    private ReturnPlurality determineSQLReturnSignature(String sqlString) {
        final String[] sql = sqlString.toUpperCase().split(" ");
        boolean isUnique =
                (sql[sql.length - 2] + " " + sql[sql.length - 1]).equals("LIMIT 1") ||
                        sql[0].equals("INSERT") ||
                        sql[0].equals("REPLACE");
        return isUnique ? ReturnPlurality.SINGLE : ReturnPlurality.BATCH;
    }

    private String parseSqlString(String sql, LinkedHashMap<String, TypeName> args, TypeElement entity) {
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
            if ((arg = parseLexemeForArg(lexeme, args, entitiesFields.get(entity))) != null) {
                args.putIfAbsent(arg.getKey(), arg.getValue());
                finalSql.append(" :").append(arg.getKey());
            } else {
                finalSql.append(" ").append(lexeme);
            }
        }
        return finalSql.toString().trim();
    }

    private Map.Entry<String, TypeName> parseLexemeForArg(
            String lexeme,
            Map<String, TypeName> argsSoFar,
            Map<String, TypeName> entityFields) {
        if (!lexeme.contains(":")) return null;
        if (lexeme.contains(":::")) {
            compilationFailureWithMessage(
                    lexeme + " contains more than two colons, " +
                            "must be either two or one for valid arg syntax.", env);
        }
        val argName = lexeme.substring(lexeme.lastIndexOf(":") + 1).replaceAll("'", "");
        TypeName argType;
        if (lexeme.contains("::")) {
            if (argsSoFar.containsKey(argName)) {
                compilationFailureWithMessage("sql argument name collision: " + argName, env);
            }
            String typeNameString = lexeme.substring(0, lexeme.indexOf(":"));
            argType = resolvePrimitiveType(typeNameString);
            if (argType == null) {
                compilationFailureWithMessage(
                        "cannot resolve type " + typeNameString +
                                " for sql argument " + argName, env);
            }
        } else {
            argType = entityFields.get(argName);
            if (argType == null)
                argType = argsSoFar.get(argName);
            if (argType == null) {
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
        if (isList) typeNameString = typeNameString.substring(0, typeNameString.indexOf("["));
        switch (typeNameString) {
            case "byte":
            case "Byte":
                argClazz = Byte.class;
                break;
            case "short":
            case "Short":
                argClazz = Short.class;
                break;
            case "int":
            case "Integer":
                argClazz = Integer.class;
                break;
            case "long":
            case "Long":
                argClazz = Long.class;
                break;
            case "float":
            case "Float":
                argClazz = Float.class;
                break;
            case "double":
            case "Double":
                argClazz = Double.class;
                break;
            case "boolean":
            case "Boolean":
                argClazz = Boolean.class;
                break;
            case "char":
            case "Character":
                argClazz = Character.class;
                break;
            case "String":
                argClazz = String.class;
                break;
            default:
                return null;
        }
        if (isList)
            return ParameterizedTypeName.get(List.class, argClazz);
        else
            return TypeName.get(argClazz);
    }

    private String formatAndValidateName(String name) {
        if (name.contains("/"))
            name = name.substring(name.lastIndexOf("/") + 1);
        name = name.trim();
        if (name.endsWith(".sql")) name = name.substring(0, name.indexOf(".sql"));
        if (name.endsWith(".jpql")) name = name.substring(0, name.indexOf(".jpql"));
        if (!isValidJavaIdentifier(name)) {
            compilationFailureWithMessage(invalidNameMessage(name), env);
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

    private static String invalidNameMessage(String name) {
        return name + " is not a valid java identifier. please verify that all names " +
                "given to custom sql queries are valid java identifiers.";
    }

    private static Map<String, TypeName> resolveFieldTypesOf(TypeElement typeElement) {
        Map<String, TypeName> result = new HashMap<>();
        for (Element field : typeElement.getEnclosedElements())
            if (field.getKind().isField())
                result.put(field.getSimpleName().toString(), ClassName.get(field.asType()));
        return result;
    }

    private String sqlResourceToString(String resourcePath) {
        try {
            FileObject sqlFileObject = env.getFiler()
                    .getResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);
            InputStream sqlFileStream = sqlFileObject.openInputStream();
            String raw = FileCopyUtils.copyToString(new InputStreamReader(sqlFileStream));
            Pattern commentPattern = Pattern.compile("(?:/\\*[^;]*?\\*/)|(?:--[^;]*?$)", Pattern.DOTALL | Pattern.MULTILINE);
            return commentPattern.matcher(raw).replaceAll("");
        } catch (IOException e) {
            compilationFailureWithMessage(e.toString(), env);
            return null;
        }
    }

}

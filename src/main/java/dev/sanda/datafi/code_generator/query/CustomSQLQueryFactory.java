package dev.sanda.datafi.code_generator.query;

import static com.google.common.collect.Maps.immutableEntry;

import com.squareup.javapoet.*;
import dev.sanda.datafi.annotations.query.WithNativeQuery;
import dev.sanda.datafi.annotations.query.WithNativeQueryScripts;
import dev.sanda.datafi.annotations.query.WithQuery;
import dev.sanda.datafi.annotations.query.WithQueryScripts;
import dev.sanda.datafi.code_generator.annotated_element_specs.EntityDalSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.FileCopyUtils;

@RequiredArgsConstructor
public class CustomSQLQueryFactory {

  @NonNull
  private final ProcessingEnvironment env;

  public Map<TypeElement, List<MethodSpec>> constructCustomQueries(
    List<EntityDalSpec> entityDalSpecs
  ) {
    Map<TypeElement, List<MethodSpec>> customQueriesMap = new HashMap<>();
    for (val entitySpec : entityDalSpecs) {
      List<CustomSQLQuery> customQueries = getCustomSQLQueries(entitySpec);
      List<MethodSpec> customQueriesMethodSpecs = new ArrayList<>();
      for (CustomSQLQuery query : customQueries) {
        customQueriesMethodSpecs.add(generateCustomQueryMethod(query));
      }
      customQueriesMap.put(entitySpec.getElement(), customQueriesMethodSpecs);
    }
    return customQueriesMap;
  }

  private MethodSpec generateCustomQueryMethod(CustomSQLQuery query) {
    AnnotationSpec.Builder queryAnnotationBuilder = AnnotationSpec
      .builder(Query.class)
      .addMember("value", "$S", query.getSql());
    if (query.isNative()) queryAnnotationBuilder.addMember(
      "nativeQuery",
      "$L",
      true
    );
    return MethodSpec
      .methodBuilder(query.getName())
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
      .addAnnotation(queryAnnotationBuilder.build())
      .addParameters(query.parameterSpecs())
      .returns(query.returnSignature())
      .build();
  }

  private List<CustomSQLQuery> getCustomSQLQueries(
    EntityDalSpec entityDalSpec
  ) {
    val individualQueries = entityDalSpec.getAnnotationsByType(WithQuery.class);
    val individualNativeQueries = entityDalSpec.getAnnotationsByType(
      WithNativeQuery.class
    );
    val queryScripts = entityDalSpec.getAnnotation(WithQueryScripts.class);
    val nativeQueryScripts = entityDalSpec.getAnnotation(
      WithNativeQueryScripts.class
    );
    val customSQLQueries = new ArrayList<CustomSQLQuery>();

    if (!individualQueries.isEmpty()) {
      for (WithQuery query : individualQueries) {
        customSQLQueries.add(
          parseQuery(query.name(), query.jpql(), entityDalSpec)
        );
      }
    }

    if (individualNativeQueries != null) {
      for (WithNativeQuery query : individualNativeQueries) {
        customSQLQueries.add(
          parseIndividualNativeQuery(query.name(), query.sql(), entityDalSpec)
        );
      }
    }

    if (queryScripts != null) {
      for (String scriptPath : queryScripts.value()) {
        customSQLQueries.add(parseQueryScript(scriptPath, entityDalSpec));
      }
    }

    if (nativeQueryScripts != null) {
      for (String scriptPath : nativeQueryScripts.value()) {
        customSQLQueries.add(parseQueryScript(scriptPath, entityDalSpec));
      }
    }
    return customSQLQueries;
  }

  private CustomSQLQuery parseQueryScript(
    String path,
    EntityDalSpec entityDalSpec
  ) {
    //get file
    final ClassPathResource resource = new ClassPathResource(path);
    //validate and assign filename as query name
    String name = formatAndValidateName(
      Objects.requireNonNull(resource.getFilename())
    );
    //read sql string from file
    String sql = sqlResourceToString(resource.getPath());
    //set nativeQuery flag
    boolean nativeQuery = determineIfIsNativeQuery(path);
    //set isNativeQuery
    CustomSQLQuery result = parseQuery(name, sql, entityDalSpec);
    result.setNative(nativeQuery);
    return result;
  }

  private boolean determineIfIsNativeQuery(String path) {
    if (path.endsWith(".sql")) return true;
    if (path.endsWith(".jpql")) return false; else {
      compilationFailureWithMessage(
        String.format(
          "Invalid query script path: %s; Paths must end with either a .sql or a .jpql suffix",
          path
        ),
        env
      );
      return false;
    }
  }

  private CustomSQLQuery parseIndividualNativeQuery(
    String name,
    String sql,
    EntityDalSpec entityDalSpec
  ) {
    CustomSQLQuery customSQLQuery = parseQuery(name, sql, entityDalSpec);
    customSQLQuery.setNative(true);
    return customSQLQuery;
  }

  private CustomSQLQuery parseQuery(
    String name,
    String sql,
    EntityDalSpec entity
  ) {
    CustomSQLQuery customSQLQuery = new CustomSQLQuery();
    customSQLQuery.setAnnotatedEntity(entity.getElement());
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

  private String parseSqlString(
    String sql,
    LinkedHashMap<String, TypeName> args,
    EntityDalSpec entity
  ) {
    String lineSeparator = System.getProperty("line.separator");
    String formattedSqlString = sql
      .replaceAll(lineSeparator, " ")
      .replaceAll("\\s+", " ")
      .trim();
    String[] lexemes = formattedSqlString.split(" ");
    StringBuilder finalSql = new StringBuilder();
    Map.Entry<String, TypeName> arg;
    for (String lexeme : lexemes) {
      if (
        (arg = parseLexemeForArg(lexeme, args, entity.getEntityFieldTypes())) !=
        null
      ) {
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
    Map<String, TypeName> entityFields
  ) {
    if (!lexeme.contains(":")) return null;
    if (lexeme.contains(":::")) {
      compilationFailureWithMessage(
        lexeme +
        " contains more than two colons, " +
        "must be either two or one for valid arg syntax.",
        env
      );
    }
    val argName = lexeme
      .substring(lexeme.lastIndexOf(":") + 1)
      .replaceAll("'", "");
    TypeName argType;
    if (lexeme.contains("::")) {
      if (argsSoFar.containsKey(argName)) {
        compilationFailureWithMessage(
          "sql argument name collision: " + argName,
          env
        );
      }
      String typeNameString = lexeme.substring(0, lexeme.indexOf(":"));
      argType = resolvePrimitiveType(typeNameString);
      if (argType == null) {
        compilationFailureWithMessage(
          "cannot resolve type " +
          typeNameString +
          " for sql argument " +
          argName,
          env
        );
      }
    } else {
      argType = entityFields.get(argName);
      if (argType == null) argType = argsSoFar.get(argName);
      if (argType == null) {
        compilationFailureWithMessage(
          lexeme +
          " is invalid syntax; must either " +
          "specify type with double colon syntax or " +
          "reference primitive field of annotated entity.",
          env
        );
      }
    }
    return immutableEntry(argName, argType);
  }

  private TypeName resolvePrimitiveType(String typeNameString) {
    Class<?> argClazz;
    boolean isList = typeNameString.endsWith("[]");
    if (isList) typeNameString =
      typeNameString.substring(0, typeNameString.indexOf("["));
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
    if (isList) return ParameterizedTypeName.get(
      List.class,
      argClazz
    ); else return TypeName.get(argClazz);
  }

  private String formatAndValidateName(String name) {
    if (name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);
    name = name.trim();
    if (name.endsWith(".sql")) name = name.substring(0, name.indexOf(".sql"));
    if (name.endsWith(".jpql")) name = name.substring(0, name.indexOf(".jpql"));
    if (!isValidJavaIdentifier(name)) {
      compilationFailureWithMessage(invalidNameMessage(name), env);
    }
    return name;
  }

  private static void compilationFailureWithMessage(
    String message,
    ProcessingEnvironment env
  ) {
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
    return (
      name +
      " is not a valid java identifier. please verify that all names " +
      "given to custom sql queries are valid java identifiers."
    );
  }

  private static Map<String, TypeName> resolveFieldTypesOf(
    TypeElement typeElement
  ) {
    Map<String, TypeName> result = new HashMap<>();
    for (Element field : typeElement.getEnclosedElements()) if (
      field.getKind().isField()
    ) result.put(
      field.getSimpleName().toString(),
      ClassName.get(field.asType())
    );
    return result;
  }

  private String sqlResourceToString(String resourcePath) {
    try {
      FileObject sqlFileObject = env
        .getFiler()
        .getResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);
      InputStream sqlFileStream = sqlFileObject.openInputStream();
      String raw = FileCopyUtils.copyToString(
        new InputStreamReader(sqlFileStream)
      );
      Pattern commentPattern = Pattern.compile(
        "(?:/\\*[^;]*?\\*/)|(?:--[^;]*?$)",
        Pattern.DOTALL | Pattern.MULTILINE
      );
      return commentPattern.matcher(raw).replaceAll("");
    } catch (IOException e) {
      compilationFailureWithMessage(e.toString(), env);
      return null;
    }
  }
}

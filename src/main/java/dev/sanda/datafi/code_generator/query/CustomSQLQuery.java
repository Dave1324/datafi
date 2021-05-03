package dev.sanda.datafi.code_generator.query;

import com.squareup.javapoet.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import lombok.Data;
import org.springframework.data.repository.query.Param;

@Data
public class CustomSQLQuery {

  private TypeElement annotatedEntity;
  private String name;
  private String sql;
  private boolean isNative = false;
  private LinkedHashMap<String, TypeName> args = new LinkedHashMap<>();
  private ReturnPlurality returnPlurality;

  public TypeName returnSignature() {
    TypeName returnType = isDtoReturnType()
      ? resolveDtoType()
      : ClassName.get(annotatedEntity);
    switch (returnPlurality) {
      case SINGLE:
        return returnType;
      case BATCH:
        return ParameterizedTypeName.get(ClassName.get(List.class), returnType);
      default:
        return null;
    }
  }

  private TypeName resolveDtoType() {
    final String dtoName = sql.split(" ")[2];
    String canonicalDtoClassName = dtoName.substring(0, dtoName.indexOf("("));
    return ClassName.bestGuess(canonicalDtoClassName);
  }

  private boolean isDtoReturnType() {
    return sql
      .toUpperCase()
      .matches("^SELECT NEW [A-Z][A-Z0-9_]*(\\.[A-Z0-9_]+)+[0-9A-Z_].+");
  }

  public List<ParameterSpec> parameterSpecs() {
    return args
      .entrySet()
      .stream()
      .map(this::queryParam)
      .collect(Collectors.toList());
  }

  private ParameterSpec queryParam(Map.Entry<String, TypeName> arg) {
    return ParameterSpec
      .builder(arg.getValue(), arg.getKey())
      .addAnnotation(
        AnnotationSpec
          .builder(Param.class)
          .addMember("value", "$S", arg.getKey())
          .build()
      )
      .build();
  }
}

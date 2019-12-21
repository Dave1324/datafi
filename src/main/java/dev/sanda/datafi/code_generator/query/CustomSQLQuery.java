package dev.sanda.datafi.code_generator.query;

import com.squareup.javapoet.*;
import lombok.Data;
import org.springframework.data.repository.query.Param;

import javax.lang.model.element.TypeElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class CustomSQLQuery {
    private TypeElement annotatedEntity;
    private String name;
    private String sql;
    private boolean isNative = false;
    private LinkedHashMap<String, TypeName> args = new LinkedHashMap<>();
    private ReturnPlurality returnSignature;

    public TypeName returnTypeName() {
        switch (returnSignature){
            case SINGLE:
                return ClassName.get(annotatedEntity);
            case BATCH:
                return ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(annotatedEntity));
            default: return null;
        }
    }

    public List<ParameterSpec> parameterSpecs() {
        return args
                .entrySet()
                .stream()
                .map(this::queryParam)
                .collect(Collectors.toList());
    }

    private ParameterSpec queryParam(Map.Entry<String, TypeName> arg) {
        return ParameterSpec.builder(arg.getValue(), arg.getKey())
                .addAnnotation(AnnotationSpec.builder(Param.class)
                        .addMember("value", "$S", arg.getKey())
                        .build())
                .build();
    }
}

package dev.sanda.datafi.code_generator.query;

import com.squareup.javapoet.*;
import lombok.Data;
import org.springframework.data.repository.query.Param;

import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class CustomSQLQuery {
    private TypeElement annotatedEntity;
    private String name;
    private String sql;
    private boolean isNative = false;
    private Map<String, TypeName> args = new HashMap<>();
    private QueryReturnSignature returnSignature;

    public TypeName returnTypeName() {
        switch (returnSignature){
            case SINGLE_RECORD:
                return ClassName.get(annotatedEntity);
            case LIST_OF_RECORDS:
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

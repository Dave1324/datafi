package dev.sanda.datafi.code_generator.annotated_element_specs;

import static dev.sanda.datafi.DatafiStaticUtils.*;

import com.squareup.javapoet.TypeName;
import dev.sanda.datafi.annotations.attributes.NonApiUpdatables;
import dev.sanda.datafi.annotations.free_text_search.WithFreeTextSearchByFields;
import dev.sanda.datafi.annotations.query.WithNativeQuery;
import dev.sanda.datafi.annotations.query.WithQuery;
import dev.sanda.datafi.annotations.query.WithQueryScripts;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.val;

public class EntityDalSpec extends AnnotatedElementSpec<TypeElement> {

  @Getter
  private List<FieldDalSpec> fieldDalSpecs;

  public Map<String, TypeName> getEntityFieldTypes() {
    return fieldDalSpecs
      .stream()
      .collect(
        Collectors.toMap(
          fieldDalSpec -> fieldDalSpec.simpleName,
          fieldDalSpec -> TypeName.get(fieldDalSpec.element.asType())
        )
      );
  }

  public EntityDalSpec(TypeElement entity, TypeElement entityApiSpec) {
    super(entity);
    if (entityApiSpec != null) addAnnotations(entityApiSpec);
    setFieldSpecs(entityApiSpec);
  }

  private void setFieldSpecs(TypeElement entityApiSpec) {
    fieldDalSpecs = new ArrayList<>();
    val entityFields = getFieldsOf(element);
    val apiSpecGetters = getGettersOf(entityApiSpec);
    val gettersByFieldNames = apiSpecGetters
      .stream()
      .collect(
        Collectors.toMap(
          getter -> getter.getSimpleName().toString().replaceFirst("get", ""),
          Function.identity()
        )
      );
    entityFields.forEach(
      field -> {
        val getter = gettersByFieldNames.get(
          toPascalCase(field.getSimpleName().toString())
        );
        if (getter != null) this.fieldDalSpecs.add(
            new FieldDalSpec(field, getter)
          ); else this.fieldDalSpecs.add(new FieldDalSpec(field));
      }
    );
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <A extends Annotation> Class<A>[] targetAnnotations() {
    return new Class[] {
      NonApiUpdatables.class,
      WithFreeTextSearchByFields.class,
      WithNativeQuery.class,
      WithQuery.class,
      WithQueryScripts.class,
    };
  }
}

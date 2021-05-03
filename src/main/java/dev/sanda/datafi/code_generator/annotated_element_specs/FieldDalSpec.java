package dev.sanda.datafi.code_generator.annotated_element_specs;

import dev.sanda.datafi.annotations.attributes.AutoSynchronized;
import dev.sanda.datafi.annotations.attributes.IsNonNullable;
import dev.sanda.datafi.annotations.attributes.NonApiUpdatable;
import dev.sanda.datafi.annotations.attributes.NonNullable;
import dev.sanda.datafi.annotations.finders.FindAllBy;
import dev.sanda.datafi.annotations.finders.FindBy;
import dev.sanda.datafi.annotations.finders.FindByUnique;
import java.lang.annotation.Annotation;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Id;

public class FieldDalSpec extends AnnotatedElementSpec<VariableElement> {

  public FieldDalSpec(VariableElement field, ExecutableElement getter) {
    this(field);
    addAnnotations(getter);
  }

  public FieldDalSpec(VariableElement field) {
    super(field);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <A extends Annotation> Class<A>[] targetAnnotations() {
    return new Class[] {
      AutoSynchronized.class,
      IsNonNullable.class,
      NonApiUpdatable.class,
      NonNullable.class,
      FindAllBy.class,
      FindBy.class,
      FindByUnique.class,
      Id.class,
      EmbeddedId.class,
      Column.class,
    };
  }
}

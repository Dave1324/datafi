package dev.sanda.datafi.code_generator.annotated_element_specs;

import lombok.Getter;
import lombok.val;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;
import java.util.*;

import static dev.sanda.datafi.DatafiStaticUtils.getDirectOrIndirectAnnotation;
import static dev.sanda.datafi.DatafiStaticUtils.isDirectlyOrIndirectlyAnnotatedAs;

@Getter
@SuppressWarnings("unchecked")
public abstract class AnnotatedElementSpec<T extends Element>{

    protected T element;
    protected String simpleName;
    protected Map<Class<? extends Annotation>, List<Annotation>> annotations = new HashMap<>();

    public AnnotatedElementSpec(T element){
        this.element = element;
        this.simpleName = element.getSimpleName().toString();
        this.addAnnotations(element);
    }

    protected void addAnnotations(Element element) {
        for (Class<? extends Annotation> targetAnnotation : targetAnnotations()) {
            val annotationsByTargetType = element.getAnnotationsByType(targetAnnotation);
            if (annotationsByTargetType.length > 0) {
                this.annotations.put(targetAnnotation, Arrays.asList(annotationsByTargetType));
            }else if(isDirectlyOrIndirectlyAnnotatedAs(element, targetAnnotation)){
                val annotation = getDirectOrIndirectAnnotation(element, targetAnnotation);
                this.annotations.put(targetAnnotation, Collections.singletonList(annotation));
            }
        }

    }

    protected abstract <A extends Annotation> Class<A>[] targetAnnotations();

    public <A extends Annotation> List<A> getAnnotationsByType(Class<A> annotationType){
        return  annotations.containsKey(annotationType)
                ? (List<A>) annotations.get(annotationType)
                : new ArrayList<>();
    }


    public <A extends Annotation> A getAnnotation(Class<A> annotationType){
        return  annotations.containsKey(annotationType)
                ? (A) annotations.get(annotationType).get(0)
                : null;
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType){
        return getAnnotation(annotationType) != null;
    }

}

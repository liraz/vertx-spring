package io.vertx.ext.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RouterRequestBody {

    /**
     * Whether body content is required.
     * <p>Default is {@code true}, leading to an exception thrown in case
     * there is no body content. Switch this to {@code false} if you prefer
     * {@code null} to be passed when the body content is {@code null}.
     */
    boolean required() default true;
}

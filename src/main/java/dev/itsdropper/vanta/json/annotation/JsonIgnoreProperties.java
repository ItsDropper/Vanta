package dev.itsdropper.vanta.json.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonIgnoreProperties { boolean ignoreUnknown() default false; }

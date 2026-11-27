package dev.itsdropper.vanta.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Captures a generic JSON target type, such as List<InstalledModRecord>. */
public abstract class TypeReference<T> {
    private final Type type;
    protected TypeReference() {
        Type parent = getClass().getGenericSuperclass();
        if (!(parent instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("TypeReference must be created with a type parameter.");
        }
        type = parameterized.getActualTypeArguments()[0];
    }
    public final Type getType() { return type; }
}

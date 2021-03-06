package com.redhat.pantheon.model.api;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A strongly typed jcr field definition for a {@link SlingModel}.
 * Field definitions have a reference to their owning object so they
 * can read and modify said owner when necessary.
 *
 * @param <T>
 * @author Carlos Munoz
 */
public interface Field<T> extends Supplier<T>, Consumer<T> {

    /**
     * Sets the value on the jcr field of the underlying resource.
     * Setting a field to null effectively removes the field from the resource.
     * @param value
     */
    void set(@Nullable T value);

    /**
     * Same as {@link #set(Object)}, just to conform to the {@link Consumer} interface.
     * @see #set(Object)
     * @param t
     */
    @Override
    default void accept(T t) {
        set(t);
    }

    /**
     * Casts this field to a different contained type. Not all conversions may work and the
     * resulting field might throw exceptions when reading or updating the value.
     * @param newFieldType The new field type to convert the field to.
     * @param <R>
     * @return A new field which produces/consumes values of a different type
     */
    <R> Field<R> toFieldType(Class<R> newFieldType);

    /**
     * Convert this Child to an {@link Optional}
     * @return An {@link Optional} with the contained value.
     */
    default Optional<T> asOptional() {
        return Optional.ofNullable(get());
    }

    /**
     * Executes a function if there is a value in the field.
     * @param consumer The function to execute when the value if present.
     */
    default void ifPresent(Consumer<T> consumer) {
        T fieldValue = get();
        if(fieldValue != null) {
            consumer.accept(fieldValue);
        }
    }
}

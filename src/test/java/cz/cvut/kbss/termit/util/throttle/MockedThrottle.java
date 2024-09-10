package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * Implementation of annotation interface allowing instancing for testing purposes
 */
public class MockedThrottle implements Throttle {

    private @NotNull String value;

    private @NotNull String group;

    public MockedThrottle(@NotNull String value, @NotNull String group) {
        this.value = value;
        this.group = group;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String group() {
        return group;
    }

    @Override
    public @Nullable String name() {
        return "NameOfMockedThrottle"+group+value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Throttle.class;
    }

    public void setValue(@NotNull String value) {
        this.value = value;
    }

    public void setGroup(@NotNull String group) {
        this.group = group;
    }
}

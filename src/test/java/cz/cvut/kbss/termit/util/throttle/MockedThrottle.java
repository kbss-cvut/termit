package cz.cvut.kbss.termit.util.throttle;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;

/**
 * Implementation of annotation interface allowing instancing for testing purposes
 */
public class MockedThrottle implements Throttle {

    private String value;

    private String group;

    public MockedThrottle(@Nonnull String value, @Nonnull String group) {
        this.value = value;
        this.group = group;
    }

    @Override
    public @Nonnull String value() {
        return value;
    }

    @Override
    public @Nonnull String group() {
        return group;
    }

    @Override
    public String name() {
        return "NameOfMockedThrottle"+group+value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Throttle.class;
    }

    public void setValue(@Nonnull String value) {
        this.value = value;
    }

    public void setGroup(@Nonnull String group) {
        this.group = group;
    }
}

package cz.cvut.kbss.termit.util.throttle;

import org.springframework.lang.NonNull;

import java.lang.annotation.Annotation;

/**
 * Implementation of annotation interface allowing instancing for testing purposes
 */
public class MockedThrottle implements Throttle {

    private String value;

    private String group;

    public MockedThrottle(@NonNull String value, @NonNull String group) {
        this.value = value;
        this.group = group;
    }

    @Override
    public @NonNull String value() {
        return value;
    }

    @Override
    public @NonNull String group() {
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

    public void setValue(@NonNull String value) {
        this.value = value;
    }

    public void setGroup(@NonNull String group) {
        this.group = group;
    }
}

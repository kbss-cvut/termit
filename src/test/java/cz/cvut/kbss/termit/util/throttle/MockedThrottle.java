package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

public class MockedThrottle implements Throttle {

    private String value;

    private String group;

    private String clearGroup;

    public MockedThrottle(String value, String group, String clearGroup) {
        this.value = value;
        this.group = group;
        this.clearGroup = clearGroup;
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
    public @NotNull String clearGroup() {
        return clearGroup;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Throttle.class;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setClearGroup(String clearGroup) {
        this.clearGroup = clearGroup;
    }
}

package cz.cvut.kbss.termit.util.throttle;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

public class MockedMethodSignature implements MethodSignature {

    private Class<?> returnType;

    private Class<?>[] parameterTypes;

    private String[] parameterNames;

    public MockedMethodSignature(Class<?> returnType, Class[] parameterTypes, String[] parameterNames) {
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.parameterNames = parameterNames;
    }

    @Override
    public Class<?> getReturnType() {
        return returnType;
    }

    @Override
    public Method getMethod() {
        return null;
    }

    @Override
    public Class[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public String[] getParameterNames() {
        return parameterNames;
    }

    @Override
    public Class[] getExceptionTypes() {
        return new Class[0];
    }

    @Override
    public String toShortString() {
        return "shortMethodSignatureString";
    }

    @Override
    public String toLongString() {
        return "longMethodSignatureString";
    }

    @Override
    public String getName() {
        return "testingMethodName";
    }

    @Override
    public int getModifiers() {
        return 0;
    }

    @Override
    public Class<?> getDeclaringType() {
        return null;
    }

    @Override
    public String getDeclaringTypeName() {
        return "";
    }

    public void setReturnType(Class<?> returnType) {
        this.returnType = returnType;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public void setParameterNames(String[] parameterNames) {
        this.parameterNames = parameterNames;
    }
}

package cz.cvut.kbss.termit.util.throttle;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * Allows construction of {@link MethodSignature} for testing purposes
 */
public class MockedMethodSignature implements MethodSignature {

    private final String methodName;
    private Class<?> returnType;

    private Class<?>[] parameterTypes;

    private String[] parameterNames;

    public MockedMethodSignature(String methodName, Class<?> returnType, Class[] parameterTypes, String[] parameterNames) {
        this.methodName = methodName;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.parameterNames = parameterNames;
    }

    @Override
    public Class<?> getReturnType() {
        return returnType;
    }

    public void setReturnType(Class<?> returnType) {
        this.returnType = returnType;
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
        return "shortMethodSignatureString" + methodName;
    }

    @Override
    public String toLongString() {
        return "longMethodSignatureString" + methodName;
    }

    @Override
    public String getName() {
        return methodName;
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
}

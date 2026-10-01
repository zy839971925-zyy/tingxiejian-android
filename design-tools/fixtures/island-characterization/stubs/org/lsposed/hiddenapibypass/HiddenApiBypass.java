package org.lsposed.hiddenapibypass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class HiddenApiBypass {
    public static Object invoke(Class<?> owner, Object receiver, String name, Object... args) throws Exception {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try { return method.invoke(receiver, args); }
                catch (InvocationTargetException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw error;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
}

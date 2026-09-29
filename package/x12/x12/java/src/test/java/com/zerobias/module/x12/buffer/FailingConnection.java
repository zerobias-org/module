package com.zerobias.module.x12.buffer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.function.Predicate;

/**
 * A real SQLite connection that throws {@link OutOfMemoryError} when it is asked to run a
 * statement whose SQL matches {@code trigger} — the way to put an {@link Error} in the middle
 * of a transaction the production code manages, after some of its work already ran.
 * {@code prepareStatement(sql)} fails at prepare time; a plain {@code Statement} fails when
 * {@code execute*(sql)} is called with matching SQL. Everything else goes to the delegate.
 */
final class FailingConnection {

    private FailingConnection() {
    }

    static Connection wrap(Connection delegate, Predicate<String> trigger) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("prepareStatement") && args != null && args[0] instanceof String sql
                        && trigger.test(sql)) {
                    throw new OutOfMemoryError("simulated: preparing " + sql);
                }
                Object out = call(method, delegate, args);
                if (name.equals("createStatement") && out instanceof Statement st) {
                    return statement(st, trigger);
                }
                return out;
            });
    }

    private static Statement statement(Statement delegate, Predicate<String> trigger) {
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().startsWith("execute") && args != null && args.length > 0
                    && args[0] instanceof String sql && trigger.test(sql)) {
                throw new OutOfMemoryError("simulated: executing " + sql);
            }
            return call(method, delegate, args);
        };
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class}, h);
    }

    private static Object call(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

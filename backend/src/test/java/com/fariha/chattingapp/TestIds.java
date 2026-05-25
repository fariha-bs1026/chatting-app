package com.fariha.chattingapp;

import java.lang.reflect.Field;

public final class TestIds {
    private TestIds() {
    }

    public static <T> T withId(T target, String id) {
        try {
            Field field = findField(target.getClass(), "id");
            field.setAccessible(true);
            field.set(target, id);
            return target;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to assign test id", exception);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}

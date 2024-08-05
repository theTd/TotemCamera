package org.totemcraft.camera;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.Field;
import java.util.Optional;

@SuppressWarnings("unchecked")
class ReflectionUtil {
    final static ReflectionRemapper REFLECTION_REMAPPER;

    final static EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID;
    final static EntityDataAccessor<Boolean> DATA_NO_GRAVITY;
    final static EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME;
    final static EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE;

    final static EntityDataAccessor<Integer> SLIME_DATA_ID_SIZE;

    static <T> T getStaticField(Class<?> clazz, String name) {
        try {
            String fieldName = REFLECTION_REMAPPER.remapFieldName(clazz, name);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        REFLECTION_REMAPPER = ReflectionRemapper.forReobfMappingsInPaperJar();
        DATA_SHARED_FLAGS_ID = getStaticField(Entity.class, "DATA_SHARED_FLAGS_ID");
        DATA_NO_GRAVITY = getStaticField(Entity.class, "DATA_NO_GRAVITY");
        DATA_CUSTOM_NAME = getStaticField(Entity.class, "DATA_CUSTOM_NAME");
        DATA_CUSTOM_NAME_VISIBLE = getStaticField(Entity.class, "DATA_CUSTOM_NAME_VISIBLE");

        SLIME_DATA_ID_SIZE = getStaticField(Slime.class, "ID_SIZE");
    }
}

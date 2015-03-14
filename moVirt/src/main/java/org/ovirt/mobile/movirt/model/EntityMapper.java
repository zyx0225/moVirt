package org.ovirt.mobile.movirt.model;

import android.database.Cursor;

import org.ovirt.mobile.movirt.model.trigger.Trigger;

@SuppressWarnings("unchecked")
public class EntityMapper<E extends BaseEntity> {

    private final Class<E> clazz;

    private EntityMapper(Class<E> clazz) {
        this.clazz = clazz;
    }

    public E fromCursor(Cursor cursor) {
        try {
            E entity = clazz.newInstance();
            entity.initFromCursor(cursor);
            return entity;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static final EntityMapper<Cluster> CLUSTER_MAPPER = new EntityMapper<>(Cluster.class);

    public static final EntityMapper<Trigger<?>> TRIGGER_MAPPER = (EntityMapper) new EntityMapper<>(Trigger.class);

    public static <E extends BaseEntity<?>> EntityMapper<E> forEntity(Class<E> clazz) {
        return new EntityMapper<>(clazz);
    }
}

package org.ovirt.mobile.movirt.model;

public enum EntityType {
    VM(Vm.class),
    HOST(Host.class),
    CLUSTER(Cluster.class);

    private final Class<? extends OVirtEntity> entityClass;

    EntityType(Class<? extends OVirtEntity> entityClass) {
        this.entityClass = entityClass;
    }

    public Class<? extends OVirtEntity> getEntityClass() {
        return entityClass;
    }
}

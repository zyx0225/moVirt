package org.ovirt.mobile.movirt.facade;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import org.ovirt.mobile.movirt.model.OVirtEntity;
import org.ovirt.mobile.movirt.model.trigger.TriggerResolver;
import org.ovirt.mobile.movirt.rest.OVirtClient;

public interface EntityFacade<E extends OVirtEntity> extends TriggerResolver<E> {

    E mapFromCursor(Cursor cursor);

    Intent getDetailIntent(E entity, Context context);

    void sync(String id, OVirtClient.Response<E> response);
}

package org.ovirt.mobile.movirt.model;

import android.content.ContentValues;
import android.net.Uri;
import android.renderscript.Element;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.ovirt.mobile.movirt.provider.OVirtContract;
import org.ovirt.mobile.movirt.util.CursorHelper;

import java.sql.Timestamp;

import static org.ovirt.mobile.movirt.provider.OVirtContract.CaCert.TABLE;

@DatabaseTable(tableName = TABLE)
public class CaCert extends BaseEntity<Integer> implements OVirtContract.CaCert {

    @Override
    public Uri getBaseUri() {
        return CONTENT_URI;
    }

    @DatabaseField(columnName = ID, id = true)
    private int id;

    @DatabaseField(columnName = CONTENT, canBeNull = false, dataType = DataType.BYTE_ARRAY)
    private byte[] content;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public ContentValues toValues() {
        ContentValues values = new ContentValues();
        values.put(ID, id);
        values.put(CONTENT, content);

        return values;
    }

    @Override
    protected void initFromCursorHelper(CursorHelper cursorHelper) {
        setId(cursorHelper.getInt(ID));
        setContent(cursorHelper.getByteArray(CONTENT));
    }
}

package org.ovirt.mobile.movirt.provider;

public enum Relation {

    IS_EQUAL(" = "),
    IS_LIKE(" LIKE "),
    NOT_EQUAL(" <> ");

    String val;


    Relation(String val) {
        this.val = val;
    }

    public String getVal() {
        return val;
    }
}

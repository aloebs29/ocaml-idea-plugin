package com.reason.lang.core.stub.type;

public class ORStubVersions {
    private ORStubVersions() {
    }

    public static final int INCLUDE = 5;
    public static final int CLASS = 3;
    public static final int CLASS_METHOD = 1;
    public static final int LET = 17;
    public static final int EXCEPTION = 8;
    public static final int EXTERNAL = 10;
    public static final int MODULE = 29;
    public static final int OBJECT_FIELD = 3;
    public static final int OPEN = 3;
    public static final int PARAMETER = 7;
    public static final int RECORD_FIELD = 6;
    public static final int TYPE = 12;
    public static final int VAL = 13;
    public static final int VARIANT = 9;

    // 11 -> 12: the build that unregistered the stub indexes wrote partial/failed stub trees for every OCaml
    // file it indexed ("Can't find stub index extension for key 'reason.open'"). Force a clean re-stub rather
    // than leave that on disk.
    public static final int OCL_FILE = 12;
    public static final int RES_FILE = 10;
    public static final int RML_FILE = 10;
}

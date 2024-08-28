package com.ericsson.nms.rv.core.util;

public enum FunctionalArea {

    CM("cm"),
    FM("fm"),
    FMR("fmr"),
    FLS("fls"),
    FMB("fmb"),
    PMR("pmr"),
    PM("pm"),
    APPLAUNCH("applaunch"),
    CMBE("cmbe"),
    CMBIL("cmbil"),
    ESM("esm"),
    CESM("cesm"),
    NBIVA("nbiva"),
    NBIVS("nbivs"),
    NETEX("netex"),
    SHM("shm"),
    SMRS("smrs"),
    UM("um"),
    SYSTEM("system"),
    AALDAP("aaldap"),
    FAN("fan"),
    DB("db"),
    SVC("svc"),
    AMOS("amos"),
    AMOSHOST("Amos Host"),
    UNKNOWN("unknown");

    private final String service;

    FunctionalArea(final String value) {
        service = value;
    }

    public String get() {
        return service;
    }

}

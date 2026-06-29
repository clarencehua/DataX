package com.alibaba.datax.plugin.writer.lancedbwriter.enums;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum SchemaCreateModeEnum {
    CREATE_IF_NOT_EXIST("createIfNotExist"),
    IGNORE("ignore"),
    RECREATE("recreate");
    String type;

    SchemaCreateModeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static SchemaCreateModeEnum getEnum(String name) {
        for (SchemaCreateModeEnum value : SchemaCreateModeEnum.values()) {
            if (value.getType().equalsIgnoreCase(name)) {
                return value;
            }
        }
        log.info("use default CREATE_IF_NOT_EXIST schema create mode");
        return CREATE_IF_NOT_EXIST;
    }
}

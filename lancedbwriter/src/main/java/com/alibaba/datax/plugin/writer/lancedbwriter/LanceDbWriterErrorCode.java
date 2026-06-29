package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.spi.ErrorCode;

public enum LanceDbWriterErrorCode implements ErrorCode {
    LANCEDB_TABLE("LanceDbWriter-01", "table process error"),
    REQUIRED_VALUE("LanceDbWriter-02", "miss required parameter");
    private final String code;
    private final String description;

    LanceDbWriterErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return String.format("Code:[%s], Description:[%s]. ", this.code, this.description);
    }
}

package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.spi.ErrorCode;

public enum LanceDbReaderErrorCode implements ErrorCode {
    LANCEDB_QUERY("LanceDbReader-01", "query table error"),
    REQUIRED_VALUE("LanceDbReader-02", "miss required parameter");
    private final String code;
    private final String description;

    LanceDbReaderErrorCode(String code, String description) {
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

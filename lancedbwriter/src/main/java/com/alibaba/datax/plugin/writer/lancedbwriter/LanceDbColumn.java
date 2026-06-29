package com.alibaba.datax.plugin.writer.lancedbwriter;

public class LanceDbColumn {
    private String name;
    private String type;
    private Integer dimension;
    private Boolean isPrimaryKey;
    private Integer maxLength;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public Boolean getPrimaryKey() {
        return isPrimaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }
}

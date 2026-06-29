package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.lancedbwriter.enums.SchemaCreateModeEnum;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class LanceDbCreateTable {

    private final Configuration config;

    LanceDbCreateTable(Configuration originalConfig) {
        this.config = originalConfig;
    }

    public void createTableByMode(LanceDbClient client) {
        String table = this.config.getString(KeyConstant.TABLE);
        String namespace = this.config.getString(KeyConstant.NAMESPACE);
        List<String> tableId = client.buildTableId(namespace, table);
        SchemaCreateModeEnum schemaCreateMode = SchemaCreateModeEnum.getEnum(
                this.config.getString(KeyConstant.SCHEMA_CREATE_MODE));
        List<LanceDbColumn> columns = JSON.parseObject(
                config.getString(KeyConstant.COLUMN), new TypeReference<List<LanceDbColumn>>() {});

        boolean exists = client.hasTable(tableId);

        if (schemaCreateMode == SchemaCreateModeEnum.RECREATE) {
            if (exists) {
                log.info("table {} exists, dropping", tableId);
                client.dropTable(tableId);
            }
            log.info("creating table {}", tableId);
            byte[] emptyArrow = ArrowDataBuilder.buildEmptyArrow(columns);
            client.createTable(tableId, emptyArrow);
        } else if (schemaCreateMode == SchemaCreateModeEnum.CREATE_IF_NOT_EXIST) {
            if (exists) {
                log.info("table {} already exists, skipping creation", tableId);
            } else {
                log.info("creating table {}", tableId);
                byte[] emptyArrow = ArrowDataBuilder.buildEmptyArrow(columns);
                client.createTable(tableId, emptyArrow);
            }
        } else if (schemaCreateMode == SchemaCreateModeEnum.IGNORE && !exists) {
            throw new RuntimeException("Table " + tableId + " does not exist");
        }
    }
}

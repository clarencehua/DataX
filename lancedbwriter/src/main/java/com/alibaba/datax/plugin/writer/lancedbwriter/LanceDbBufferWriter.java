package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.lancedbwriter.enums.WriteModeEnum;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LanceDbBufferWriter {

    private final LanceDbClient client;
    private final List<String> tableId;
    private final Integer batchSize;
    private final List<Record> dataCache;
    private final List<LanceDbColumn> columns;
    private final WriteModeEnum writeMode;
    private final String onColumn;
    private final boolean localMode;
    private final String uri;
    private boolean firstCommit = true;

    public LanceDbBufferWriter(LanceDbClient client, Configuration writerSliceConfig) {
        this.client = client;
        String table = writerSliceConfig.getString(KeyConstant.TABLE);
        String namespace = writerSliceConfig.getString(KeyConstant.NAMESPACE);
        this.uri = writerSliceConfig.getString(KeyConstant.URI);
        this.localMode = StringUtils.isNotBlank(uri) && client == null;
        this.tableId = client != null ? client.buildTableId(namespace, table) : null;
        this.batchSize = writerSliceConfig.getInt(KeyConstant.BATCH_SIZE, 100);
        this.dataCache = new ArrayList<>(batchSize);
        this.columns = JSON.parseObject(
                writerSliceConfig.getString(KeyConstant.COLUMN),
                new TypeReference<List<LanceDbColumn>>() {});
        this.writeMode = WriteModeEnum.getEnum(writerSliceConfig.getString(KeyConstant.WRITE_MODE));
        String pk = null;
        for (LanceDbColumn col : columns) {
            if (col.getPrimaryKey() != null && col.getPrimaryKey()) {
                pk = col.getName();
                break;
            }
        }
        this.onColumn = pk;
    }

    public void add(Record record, TaskPluginCollector taskPluginCollector) {
        try {
            dataCache.add(record);
        } catch (Exception e) {
            taskPluginCollector.collectDirtyRecord(record,
                    String.format("parse record error errorMessage: %s", e.getMessage()));
        }
    }

    public Boolean needCommit() {
        return dataCache.size() >= batchSize;
    }

    public void commit() {
        if (dataCache.isEmpty()) {
            log.info("dataCache is empty, skip commit");
            return;
        }
        try {
            byte[] arrowData = ArrowDataBuilder.buildArrow(columns, dataCache);

            if (localMode) {
                Path filePath = Paths.get(uri);
                if (firstCommit) {
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, arrowData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    firstCommit = false;
                    log.info("wrote {} records to local file {}", dataCache.size(), uri);
                } else {
                    log.warn("local mode only supports single batch write, appending skipped");
                }
            } else {
                if (writeMode == WriteModeEnum.UPSERT && onColumn != null) {
                    log.info("merge inserting {} records into {}", dataCache.size(), tableId);
                    client.mergeInsert(tableId, onColumn, arrowData);
                } else {
                    log.info("inserting {} records into {}", dataCache.size(), tableId);
                    client.insert(tableId, arrowData);
                }
            }
        } catch (Exception e) {
            log.error("commit failed for {} records", dataCache.size(), e);
            throw new RuntimeException("Failed to commit data to LanceDB", e);
        }
        dataCache.clear();
    }

    public int getDataCacheSize() {
        return dataCache.size();
    }
}

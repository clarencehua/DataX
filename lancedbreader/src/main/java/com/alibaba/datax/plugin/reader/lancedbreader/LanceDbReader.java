package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.spi.Reader;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class LanceDbReader extends Reader {
    public static class Job extends Reader.Job {
        private Configuration originalConfig = null;
        private boolean localMode = false;

        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();
            String uri = originalConfig.getString(KeyConstant.URI);
            this.localMode = StringUtils.isNotBlank(uri);
            if (localMode) {
                if (StringUtils.isBlank(uri)) {
                    throw DataXException.asDataXException(LanceDbReaderErrorCode.REQUIRED_VALUE,
                            "uri is required in local mode");
                }
            } else {
                originalConfig.getNecessaryValue(KeyConstant.API_KEY, LanceDbReaderErrorCode.REQUIRED_VALUE);
                originalConfig.getNecessaryValue(KeyConstant.DATABASE, LanceDbReaderErrorCode.REQUIRED_VALUE);
            }
            originalConfig.getNecessaryValue(KeyConstant.TABLE, LanceDbReaderErrorCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(KeyConstant.COLUMN, LanceDbReaderErrorCode.REQUIRED_VALUE);
        }

        @Override
        public void prepare() {
            if (localMode) {
                String uri = originalConfig.getString(KeyConstant.URI);
                if (!Files.exists(Paths.get(uri))) {
                    throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                            "local file does not exist: " + uri);
                }
                log.info("local mode, using file: {}", uri);
                return;
            }
            LanceDbClient client = new LanceDbClient(originalConfig);
            try {
                String table = originalConfig.getString(KeyConstant.TABLE);
                String namespace = originalConfig.getString(KeyConstant.NAMESPACE);
                List<String> tableId = client.buildTableId(namespace, table);
                if (!client.describeTable(tableId).getSchema().getFields().isEmpty()) {
                    log.info("table {} exists", tableId);
                }
            } catch (Exception e) {
                throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                        "table does not exist or connection failed", e);
            } finally {
                client.close();
            }
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            List<Configuration> configList = new ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                configList.add(this.originalConfig.clone());
            }
            return configList;
        }

        @Override
        public void destroy() {

        }
    }

    public static class Task extends Reader.Task {

        private LanceDbClient client;
        private List<String> tableId;
        private List<LanceDbColumn> columns;
        private String filter;
        private List<String> columnNames;
        private int batchSize;
        private boolean localMode = false;
        private String uri;

        @Override
        public void init() {
            log.info("Initializing LanceDB reader");
            Configuration readerSliceConfig = this.getPluginJobConf();
            this.uri = readerSliceConfig.getString(KeyConstant.URI);
            this.localMode = StringUtils.isNotBlank(uri);
            if (!localMode) {
                this.client = new LanceDbClient(readerSliceConfig);
            }
            String table = readerSliceConfig.getString(KeyConstant.TABLE);
            String namespace = readerSliceConfig.getString(KeyConstant.NAMESPACE);
            if (client != null) {
                this.tableId = client.buildTableId(namespace, table);
            }
            this.columns = JSON.parseObject(
                    readerSliceConfig.getString(KeyConstant.COLUMN),
                    new TypeReference<List<LanceDbColumn>>() {});
            this.columnNames = columns.stream().map(LanceDbColumn::getName).collect(Collectors.toList());
            this.filter = readerSliceConfig.getString(KeyConstant.FILTER);
            this.batchSize = readerSliceConfig.getInt(KeyConstant.BATCH_SIZE, 10000);
            log.info("LanceDB reader initialized");
        }

        @Override
        public void startRead(RecordSender recordSender) {
            if (localMode) {
                log.info("reading from local file: {}", uri);
                try {
                    byte[] data = Files.readAllBytes(Paths.get(uri));
                    ArrowDataParser.parseAndSend(data, columns, recordSender);
                } catch (IOException e) {
                    throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                            "failed to read local file: " + e.getMessage(), e);
                }
                return;
            }
            log.info("querying table {} with columns {}", tableId, columnNames);
            try {
                byte[] result = client.queryTable(tableId, columnNames, filter, batchSize, null);
                ArrowDataParser.parseAndSend(result, columns, recordSender);
            } catch (Exception e) {
                throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                        "query failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void destroy() {
            if (this.client != null) {
                this.client.close();
            }
        }
    }
}

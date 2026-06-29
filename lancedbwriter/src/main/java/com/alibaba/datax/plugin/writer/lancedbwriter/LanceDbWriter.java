package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LanceDbWriter extends Writer {
    public static class Job extends Writer.Job {
        private Configuration originalConfig = null;
        private boolean localMode = false;

        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();
            String uri = originalConfig.getString(KeyConstant.URI);
            this.localMode = StringUtils.isNotBlank(uri);
            if (localMode) {
                if (StringUtils.isBlank(uri)) {
                    throw DataXException.asDataXException(LanceDbWriterErrorCode.REQUIRED_VALUE,
                            "uri is required in local mode");
                }
            } else {
                originalConfig.getNecessaryValue(KeyConstant.API_KEY, LanceDbWriterErrorCode.REQUIRED_VALUE);
                originalConfig.getNecessaryValue(KeyConstant.DATABASE, LanceDbWriterErrorCode.REQUIRED_VALUE);
            }
            originalConfig.getNecessaryValue(KeyConstant.TABLE, LanceDbWriterErrorCode.REQUIRED_VALUE);
            originalConfig.getNecessaryValue(KeyConstant.COLUMN, LanceDbWriterErrorCode.REQUIRED_VALUE);
        }

        @Override
        public void prepare() {
            if (localMode) {
                log.info("local mode, will write to: {}", originalConfig.getString(KeyConstant.URI));
                return;
            }
            LanceDbClient client = new LanceDbClient(originalConfig);
            try {
                LanceDbCreateTable createTable = new LanceDbCreateTable(originalConfig);
                createTable.createTableByMode(client);
            } catch (Exception e) {
                throw DataXException.asDataXException(LanceDbWriterErrorCode.LANCEDB_TABLE, e.getMessage(), e);
            } finally {
                client.close();
            }
        }

        @Override
        public List<Configuration> split(int mandatoryNumber) {
            List<Configuration> configList = new ArrayList<>();
            for (int i = 0; i < mandatoryNumber; i++) {
                configList.add(this.originalConfig.clone());
            }
            return configList;
        }

        @Override
        public void destroy() {

        }
    }

    public static class Task extends Writer.Task {

        private LanceDbBufferWriter bufferWriter;
        private LanceDbClient client;
        private boolean localMode = false;
        private String uri;

        @Override
        public void init() {
            log.info("Initializing LanceDB writer");
            Configuration writerSliceConfig = this.getPluginJobConf();
            this.uri = writerSliceConfig.getString(KeyConstant.URI);
            this.localMode = StringUtils.isNotBlank(uri);
            if (!localMode) {
                this.client = new LanceDbClient(writerSliceConfig);
            }
            this.bufferWriter = new LanceDbBufferWriter(this.client, writerSliceConfig);
            log.info("LanceDB writer initialized");
        }

        @Override
        public void startWrite(RecordReceiver lineReceiver) {
            Record record;
            while ((record = lineReceiver.getFromReader()) != null) {
                bufferWriter.add(record, this.getTaskPluginCollector());
                if (bufferWriter.needCommit()) {
                    log.info("begin committing data size[{}]", bufferWriter.getDataCacheSize());
                    bufferWriter.commit();
                }
            }
            if (bufferWriter.getDataCacheSize() > 0) {
                log.info("begin committing data size[{}]", bufferWriter.getDataCacheSize());
                bufferWriter.commit();
            }
        }

        @Override
        public void prepare() {
            super.prepare();
        }

        @Override
        public void destroy() {
            if (this.client != null) {
                this.client.close();
            }
        }
    }
}

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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Slf4j
public class LanceDbReader extends Reader {
    public static class Job extends Reader.Job {
        private Configuration originalConfig = null;
        private boolean localMode = false;

        @Override
        public void init() {
            this.originalConfig = super.getPluginJobConf();
            String mode = originalConfig.getString(KeyConstant.MODE, KeyConstant.MODE_CLOUD);
            this.localMode = KeyConstant.MODE_LOCAL.equalsIgnoreCase(mode);
            if (localMode) {
                originalConfig.getNecessaryValue(KeyConstant.URI, LanceDbReaderErrorCode.REQUIRED_VALUE);
            } else {
                originalConfig.getNecessaryValue(KeyConstant.API_KEY, LanceDbReaderErrorCode.REQUIRED_VALUE);
                originalConfig.getNecessaryValue(KeyConstant.DATABASE, LanceDbReaderErrorCode.REQUIRED_VALUE);
                originalConfig.getNecessaryValue(KeyConstant.TABLE, LanceDbReaderErrorCode.REQUIRED_VALUE);
            }
            originalConfig.getNecessaryValue(KeyConstant.COLUMN, LanceDbReaderErrorCode.REQUIRED_VALUE);
        }

        @Override
        public void prepare() {
            if (localMode) {
                String uri = originalConfig.getString(KeyConstant.URI);
                if (uri != null && uri.startsWith(KeyConstant.SCHEME_S3)) {
                    log.info("s3 mode, will read from: {}", uri);
                    return;
                }
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
        private boolean s3Mode = false;
        private String uri;
        private S3Client s3Client;
        private String s3Bucket;
        private String s3Key;

        @Override
        public void init() {
            log.info("Initializing LanceDB reader");
            Configuration readerSliceConfig = this.getPluginJobConf();
            String mode = readerSliceConfig.getString(KeyConstant.MODE, KeyConstant.MODE_CLOUD);
            this.localMode = KeyConstant.MODE_LOCAL.equalsIgnoreCase(mode);
            this.uri = readerSliceConfig.getString(KeyConstant.URI);
            this.s3Mode = localMode && uri != null && uri.startsWith(KeyConstant.SCHEME_S3);
            if (s3Mode) {
                Configuration s3Conf = readerSliceConfig.getConfiguration(KeyConstant.S3);
                // parse bucket and key from s3://bucket/key URI
                String path = uri.substring(KeyConstant.SCHEME_S3.length());
                int slashIdx = path.indexOf('/');
                if (slashIdx > 0) {
                    this.s3Bucket = path.substring(0, slashIdx);
                    this.s3Key = path.substring(slashIdx + 1);
                } else {
                    this.s3Bucket = path;
                    this.s3Key = "";
                }
                // allow explicit override from s3 config
                String cfgBucket = s3Conf.getString(KeyConstant.S3_BUCKET);
                if (cfgBucket != null && !cfgBucket.isEmpty()) {
                    this.s3Bucket = cfgBucket;
                }
                this.s3Client = buildS3Client(s3Conf);
            } else {
                this.s3Bucket = null;
                this.s3Key = null;
                this.s3Client = null;
            }
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
                if (s3Mode) {
                    log.info("reading from s3://{}/{}", s3Bucket, s3Key);
                    try {
                        GetObjectRequest getReq = GetObjectRequest.builder()
                                .bucket(s3Bucket)
                                .key(s3Key)
                                .build();
                        byte[] data = s3Client.getObject(getReq, ResponseTransformer.toBytes()).asByteArray();
                        ArrowDataParser.parseAndSend(data, columns, recordSender);
                    } catch (NoSuchKeyException e) {
                        throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                                "s3 object not found: s3://" + s3Bucket + "/" + s3Key, e);
                    } catch (Exception e) {
                        throw DataXException.asDataXException(LanceDbReaderErrorCode.LANCEDB_QUERY,
                                "failed to read from s3: " + e.getMessage(), e);
                    }
                    return;
                }
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
            if (this.s3Client != null) {
                try { this.s3Client.close(); } catch (Exception ignore) {}
            }
        }

        private static S3Client buildS3Client(Configuration s3Conf) {
            try {
                String endpoint = s3Conf.getString(KeyConstant.S3_ENDPOINT);
                String region = s3Conf.getString(KeyConstant.S3_REGION, "us-east-1");
                String accessKey = s3Conf.getString(KeyConstant.S3_ACCESS_KEY);
                String secretKey = s3Conf.getString(KeyConstant.S3_SECRET_KEY);
                return S3Client.builder()
                        .endpointOverride(new URI(endpoint))
                        .region(Region.of(region))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to build S3 client", e);
            }
        }
    }
}

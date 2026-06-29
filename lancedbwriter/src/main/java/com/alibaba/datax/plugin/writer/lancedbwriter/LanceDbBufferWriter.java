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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
    private final boolean s3Mode;
    private final String uri;
    private final String s3Bucket;
    private final String s3Key;
    private S3Client s3Client;
    private boolean firstCommit = true;

    public LanceDbBufferWriter(LanceDbClient client, Configuration writerSliceConfig) {
        this.client = client;
        String table = writerSliceConfig.getString(KeyConstant.TABLE);
        String namespace = writerSliceConfig.getString(KeyConstant.NAMESPACE);
        String mode = writerSliceConfig.getString(KeyConstant.MODE, KeyConstant.MODE_CLOUD);
        this.localMode = KeyConstant.MODE_LOCAL.equalsIgnoreCase(mode);
        this.uri = writerSliceConfig.getString(KeyConstant.URI);
        this.s3Mode = localMode && uri != null && uri.startsWith(KeyConstant.SCHEME_S3);
        if (s3Mode) {
            Configuration s3Conf = writerSliceConfig.getConfiguration(KeyConstant.S3);
            // parse bucket and key from s3://bucket/key URI
            String path = uri.substring(KeyConstant.SCHEME_S3.length());
            int slashIdx = path.indexOf('/');
            String bucket;
            String key;
            if (slashIdx > 0) {
                bucket = path.substring(0, slashIdx);
                key = path.substring(slashIdx + 1);
            } else {
                bucket = path;
                key = "";
            }
            // allow explicit override from s3 config
            String cfgBucket = s3Conf.getString(KeyConstant.S3_BUCKET);
            if (cfgBucket != null && !cfgBucket.isEmpty()) {
                bucket = cfgBucket;
            }
            this.s3Bucket = bucket;
            this.s3Key = key;
            this.s3Client = buildS3Client(s3Conf);
        } else {
            this.s3Bucket = null;
            this.s3Key = null;
            this.s3Client = null;
        }
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
                if (s3Mode) {
                    if (firstCommit) {
                        PutObjectRequest putReq = PutObjectRequest.builder()
                                .bucket(s3Bucket)
                                .key(s3Key)
                                .build();
                        s3Client.putObject(putReq, RequestBody.fromBytes(arrowData));
                        firstCommit = false;
                        log.info("wrote {} records to s3://{}/{}", dataCache.size(), s3Bucket, s3Key);
                    } else {
                        log.warn("s3 local mode only supports single batch write, appending skipped");
                    }
                } else {
                    Path filePath = Paths.get(uri);
                    if (firstCommit) {
                        Files.createDirectories(filePath.getParent());
                        Files.write(filePath, arrowData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        firstCommit = false;
                        log.info("wrote {} records to local file {}", dataCache.size(), uri);
                    } else {
                        log.warn("local mode only supports single batch write, appending skipped");
                    }
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

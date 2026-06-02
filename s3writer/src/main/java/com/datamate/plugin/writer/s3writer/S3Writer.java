package com.datamate.plugin.writer.s3writer;

import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.CommonErrorCode;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

public class S3Writer extends Writer {

    private static final Logger LOG = LoggerFactory.getLogger(S3Writer.class);

    private static final String DEFAULT_ENCODING = "utf-8";
    private static final String DEFAULT_FIELD_DELIMITER = ",";
    private static final long DEFAULT_MAX_FILE_SIZE = 1024L; // MB

    public static class Job extends Writer.Job {
        private Configuration jobConfig = null;

        @Override
        public void init() {
            this.jobConfig = super.getPluginJobConf();
            this.jobConfig.getNecessaryValue("endpoint", CommonErrorCode.CONFIG_ERROR);
            this.jobConfig.getNecessaryValue("bucket", CommonErrorCode.CONFIG_ERROR);
            this.jobConfig.getNecessaryValue("accessKey", CommonErrorCode.CONFIG_ERROR);
            this.jobConfig.getNecessaryValue("secretKey", CommonErrorCode.CONFIG_ERROR);
            this.jobConfig.getNecessaryValue("object", CommonErrorCode.CONFIG_ERROR);
        }

        @Override
        public void prepare() {
            String bucket = this.jobConfig.getString("bucket");
            String writeMode = this.jobConfig.getString("writeMode", "truncate");
            String object = this.jobConfig.getString("object");

            S3Client s3 = getS3Client(this.jobConfig);

            if (!doesBucketExist(s3, bucket)) {
                String message = String.format("bucket [%s] does not exist", bucket);
                throw DataXException.asDataXException(CommonErrorCode.CONFIG_ERROR, message);
            }

            if ("truncate".equalsIgnoreCase(writeMode)) {
                deleteObjectsWithPrefix(s3, bucket, object);
            } else if ("nonConflict".equalsIgnoreCase(writeMode)) {
                ListObjectsV2Response resp = s3.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(bucket).prefix(object).build());
                if (resp.hasContents()) {
                    throw DataXException.asDataXException(CommonErrorCode.CONFIG_ERROR,
                            String.format("objects with prefix [%s] already exist in bucket [%s]", object, bucket));
                }
            }

            s3.close();
        }

        @Override
        public List<Configuration> split(int adviceNumber) {
            List<Configuration> configs = new ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                configs.add(this.jobConfig.clone());
            }
            return configs;
        }

        @Override
        public void post() {
        }

        @Override
        public void destroy() {
        }

        private static S3Client getS3Client(Configuration config) {
            String endpoint = config.getString("endpoint");
            String accessKey = config.getString("accessKey");
            String secretKey = config.getString("secretKey");
            String region = config.getString("region", "us-east-1");
            try {
                return S3Client.builder()
                        .endpointOverride(new URI(endpoint))
                        .region(Region.of(region))
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        .build();
            } catch (Exception e) {
                throw DataXException.asDataXException(CommonErrorCode.RUNTIME_ERROR, e);
            }
        }

        private static boolean doesBucketExist(S3Client s3, String bucket) {
            try {
                s3.headBucket(b -> b.bucket(bucket));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static void deleteObjectsWithPrefix(S3Client s3, String bucket, String prefix) {
            boolean truncated = true;
            String continuationToken = null;
            while (truncated) {
                ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix);
                if (continuationToken != null) {
                    reqBuilder.continuationToken(continuationToken);
                }
                ListObjectsV2Response resp = s3.listObjectsV2(reqBuilder.build());
                if (resp.hasContents()) {
                    List<ObjectIdentifier> toDelete = new ArrayList<>();
                    for (S3Object obj : resp.contents()) {
                        toDelete.add(ObjectIdentifier.builder().key(obj.key()).build());
                    }
                    if (!toDelete.isEmpty()) {
                        s3.deleteObjects(DeleteObjectsRequest.builder()
                                .bucket(bucket)
                                .delete(Delete.builder().objects(toDelete).build())
                                .build());
                    }
                }
                truncated = resp.isTruncated();
                continuationToken = resp.nextContinuationToken();
            }
        }
    }

    public static class Task extends Writer.Task {
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        private Configuration jobConfig;
        private S3Client s3;
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String objectPrefix;
        private String region;
        private String fieldDelimiter;
        private String encoding;
        private String nullFormat;
        private String dateFormat;
        private DateFormat dateParse;
        private long maxFileSize;
        private String suffix;

        @Override
        public void init() {
            this.jobConfig = super.getPluginJobConf();
            this.endpoint = this.jobConfig.getString("endpoint");
            this.accessKey = this.jobConfig.getString("accessKey");
            this.secretKey = this.jobConfig.getString("secretKey");
            this.bucket = this.jobConfig.getString("bucket");
            this.objectPrefix = this.jobConfig.getString("object");
            this.region = this.jobConfig.getString("region", "us-east-1");
            this.fieldDelimiter = this.jobConfig.getString("fieldDelimiter", DEFAULT_FIELD_DELIMITER);
            this.encoding = this.jobConfig.getString("encoding", DEFAULT_ENCODING);
            this.nullFormat = this.jobConfig.getString("nullFormat", "null");
            this.dateFormat = this.jobConfig.getString("dateFormat", null);
            if (StringUtils.isNotBlank(this.dateFormat)) {
                this.dateParse = new SimpleDateFormat(dateFormat);
            }
            this.maxFileSize = this.jobConfig.getLong("maxFileSize", DEFAULT_MAX_FILE_SIZE);
            this.suffix = this.jobConfig.getString("suffix", "");
            this.s3 = getS3Client();
        }

        private S3Client getS3Client() {
            try {
                return S3Client.builder()
                        .endpointOverride(new URI(endpoint))
                        .region(Region.of(region))
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        .build();
            } catch (Exception e) {
                LOG.error("Error init S3 client: {}", this.endpoint, e);
                throw DataXException.asDataXException(CommonErrorCode.RUNTIME_ERROR, e);
            }
        }

        @Override
        public void startWrite(RecordReceiver lineReceiver) {
            Record record;
            StringWriter sw = new StringWriter();
            long currentFileSize = 0;
            int fileIndex = 0;
            String currentObject = getObjectKey(fileIndex);
            boolean gotData = false;

            try {
                while ((record = lineReceiver.getFromReader()) != null) {
                    gotData = true;
                    String line = recordToString(record);
                    sw.write(line);
                    currentFileSize += line.getBytes(Charset.forName(encoding)).length;

                    if (currentFileSize >= this.maxFileSize * 1024 * 1024) {
                        uploadString(currentObject, sw.toString());
                        sw.getBuffer().setLength(0);
                        currentFileSize = 0;
                        fileIndex++;
                        currentObject = getObjectKey(fileIndex);
                    }
                }

                if (gotData && sw.getBuffer().length() > 0) {
                    uploadString(currentObject, sw.toString());
                }

                LOG.info("S3 write completed. total objects written to bucket: {}", bucket);
            } catch (Exception e) {
                LOG.error("Error writing to S3 compatible storage: {}", this.endpoint, e);
                throw DataXException.asDataXException(CommonErrorCode.RUNTIME_ERROR, e);
            } finally {
                if (s3 != null) {
                    try { s3.close(); } catch (Exception ignore) {}
                }
            }
        }

        private String getObjectKey(int fileIndex) {
            String key = objectPrefix;
            if (fileIndex > 0) {
                key += "__" + fileIndex;
            }
            if (StringUtils.isNotBlank(suffix)) {
                key += suffix;
            }
            return key;
        }

        private String recordToString(Record record) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < record.getColumnNumber(); i++) {
                if (i > 0) {
                    sb.append(fieldDelimiter);
                }
                Column column = record.getColumn(i);
                if (column == null || column.getRawData() == null) {
                    sb.append(nullFormat);
                } else {
                    String val;
                    switch (column.getType()) {
                        case DATE:
                            if (dateParse != null && column.asDate() != null) {
                                val = dateParse.format(column.asDate());
                            } else {
                                val = column.asString();
                            }
                            break;
                        case BYTES:
                            val = column.asString();
                            break;
                        default:
                            val = column.asString();
                            break;
                    }
                    sb.append(val);
                }
            }
            sb.append("\n");
            return sb.toString();
        }

        private void uploadString(String objectKey, String content) {
            try {
                byte[] bytes = content.getBytes(Charset.forName(encoding));
                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build();
                s3.putObject(putReq, RequestBody.fromBytes(bytes));
                LOG.info("Uploaded object s3://{}/{} ({} bytes)", bucket, objectKey, bytes.length);
            } catch (Exception e) {
                LOG.error("Failed to upload object s3://{}/{}", bucket, objectKey, e);
                throw DataXException.asDataXException(CommonErrorCode.RUNTIME_ERROR, e);
            }
        }

        @Override
        public void destroy() {
        }
    }
}

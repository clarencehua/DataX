package com.alibaba.datax.plugin.writer.lancedbwriter;

public class KeyConstant {
    public static final String MODE = "mode";
    public static final String API_KEY = "apiKey";
    public static final String ENDPOINT = "endpoint";
    public static final String DATABASE = "database";
    public static final String TABLE = "table";
    public static final String NAMESPACE = "namespace";
    public static final String BATCH_SIZE = "batchSize";
    public static final String COLUMN = "column";
    public static final String SCHEMA_CREATE_MODE = "schemaCreateMode";
    public static final String WRITE_MODE = "writeMode";
    public static final String REGION = "region";
    public static final String URI = "uri";

    // mode values
    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";

    // s3 config keys
    public static final String S3 = "s3";
    public static final String S3_ENDPOINT = "endpoint";
    public static final String S3_REGION = "region";
    public static final String S3_ACCESS_KEY = "accessKey";
    public static final String S3_SECRET_KEY = "secretKey";
    public static final String S3_BUCKET = "bucket";

    // uri schemes
    public static final String SCHEME_S3 = "s3://";
}

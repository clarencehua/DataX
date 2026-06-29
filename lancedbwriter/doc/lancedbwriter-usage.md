# lancedbwriter 使用文档

## 1. 功能说明
将数据写入 LanceDB 表。

## 2. 参数说明

| 参数名 | 必选 | 默认值 | 描述 |
|--------|------|--------|------|
| mode | 否 | cloud | 运行模式：`local`（本地文件或 S3，非 LanceDB 原生 .lance 格式）或 `cloud`（LanceDB Cloud） |
| uri | 模式=local时必选 | 无 | 本地 Arrow IPC 文件路径 或 `s3://bucket/key` 格式（仅 local 模式，产物是 Apache Arrow IPC File） |
| s3 | uri 以 s3:// 开头时必选 | 无 | S3 兼容存储连接配置（MinIO / Ceph / OSS 等），见下方 s3 子参数 |
| s3.endpoint | 是 | 无 | S3 兼容存储地址，如 `http://minio:9000` |
| s3.bucket | 是 | 无 | S3 bucket 名称 |
| s3.accessKey | 是 | 无 | 访问密钥 |
| s3.secretKey | 是 | 无 | 私有密钥 |
| s3.region | 否 | us-east-1 | S3 区域 |
| apiKey | 模式=cloud时必选 | 无 | LanceDB API 密钥 |
| endpoint | 否 | 无 | LanceDB Enterprise 自定义端点 |
| database | 模式=cloud时必选 | 无 | 数据库名称 |
| table | 模式=cloud时必选 | 无 | 目标表名称 |
| namespace | 否 | 空 | 命名空间路径（可多级） |
| batchSize | 否 | 100 | 批量提交记录数 |
| schemaCreateMode | 否 | createIfNotExist | 表创建模式 |
| writeMode | 否 | insert | 写入模式 |
| region | 否 | us-east-1 | AWS 区域 |
| column | 是 | 无 | 字段定义列表 |

### writeMode 说明
- `insert`: 追加写入（默认）
- `upsert`: 按主键合并写入（需要 column 中设置 primaryKey）

### schemaCreateMode 说明
- `createIfNotExist`: 表不存在则创建（默认）
- `ignore`: 表不存在则报错
- `recreate`: 删除表并重建

## 3. 配置示例

### 3.1 本地模式（Arrow IPC 文件）

> 注意：本地模式写入的是 Apache Arrow IPC File 格式，不是 LanceDB 原生的 .lance 文件。如需向量索引、ANN 检索、版本管理等能力，请使用 cloud 模式连接 LanceDB Cloud。

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "value": 1, "type": "long" },
              { "value": "hello", "type": "string" },
              { "value": 3.14, "type": "double" },
              { "value": true, "type": "bool" }
            ],
            "sliceRecordCount": 5
          }
        },
        "writer": {
          "name": "lancedbwriter",
          "parameter": {
            "mode": "local",
            "uri": "/tmp/output.arrow",
            "column": [
              { "name": "id", "type": "Int64" },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" }
            ]
          }
        }
      }
    ],
    "setting": {
      "speed": {
        "channel": 1
      }
    }
  }
}
```

### 3.2 S3 模式

> 支持 MinIO、Ceph、阿里云 OSS 等所有 S3 兼容对象存储。

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "value": 1, "type": "long" },
              { "value": "hello", "type": "string" },
              { "value": 3.14, "type": "double" },
              { "value": true, "type": "bool" }
            ],
            "sliceRecordCount": 5
          }
        },
        "writer": {
          "name": "lancedbwriter",
          "parameter": {
            "mode": "local",
            "uri": "s3://my-bucket/data/output.arrow",
            "s3": {
              "endpoint": "http://minio:9000",
              "region": "us-east-1",
              "accessKey": "minioadmin",
              "secretKey": "minioadmin"
            },
            "column": [
              { "name": "id", "type": "Int64" },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" }
            ]
          }
        }
      }
    ],
    "setting": {
      "speed": {
        "channel": 1
      }
    }
  }
}
```

### 3.3 远程模式

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "value": 1, "type": "long" },
              { "value": "[1.0, 2.0, 3.0, 4.0]", "type": "string" },
              { "value": "Hello LanceDB", "type": "string" },
              { "value": 3.14, "type": "double" },
              { "value": true, "type": "bool" }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "lancedbwriter",
          "parameter": {
            "mode": "cloud",
            "database": "your_database",
            "table": "datax_test_table",
            "schemaCreateMode": "recreate",
            "writeMode": "insert",
            "batchSize": 100,
            "column": [
              { "name": "id", "type": "Int64", "primaryKey": true },
              { "name": "embedding", "type": "FloatVector", "dimension": 4 },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" }
            ]
          }
        }
      }
    ],
    "setting": {
      "speed": {
        "channel": 1
      }
    }
  }
}
```

## 4. 运行

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_lancedbwriter.json
```

## 5. 注意事项

- `column` 中字段顺序必须与 reader 的输出列一一对应
- 向量数据以 JSON 数组字符串形式传入，如 `[1.0, 2.0, 3.0, 4.0]`
- FloatVector 类型需要指定 dimension
- upsert 模式需要指定 primaryKey
- local 模式写入的是 Apache Arrow IPC File，不是 LanceDB 原生 .lance 文件，无法直接被 lancedb Python 本地库打开或查询
- local 模式仅支持单批次写入（后续批次将被丢弃），不支持增量 append、向量索引等 LanceDB 高级特性
- S3 模式通过 `s3://bucket/key` 格式的 uri 触发，同时需配置 `s3` 子对象提供连接信息，支持 MinIO / Ceph / 阿里云 OSS 等所有 S3 兼容存储

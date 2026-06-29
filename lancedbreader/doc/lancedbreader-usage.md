# lancedbreader 使用文档

## 1. 功能说明
从 LanceDB 表读取数据。

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
| namespace | 否 | 空 | 命名空间路径 |
| column | 是 | 无 | 字段定义列表 |
| column[].name | 是 | 无 | 字段名 |
| column[].type | 是 | 无 | 数据类型 |
| column[].dimension | 否 | 无 | FloatVector 维度 |
| filter | 否 | 空 | SQL 过滤条件 |
| batchSize | 否 | 10000 | 单次查询返回的最大行数 |

## 3. 配置示例

### 3.1 本地模式（Arrow IPC 文件）

> 注意：本地模式读写的是 Apache Arrow IPC File 格式，不是 LanceDB 原生的 .lance 文件。如需 ANN 检索、向量索引、版本管理等能力，请使用 cloud 模式连接 LanceDB Cloud。

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "lancedbreader",
          "parameter": {
            "mode": "local",
            "uri": "/path/to/data.arrow",
            "column": [
              { "name": "id", "type": "Int64" },
              { "name": "name", "type": "String" }
            ]
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": {
            "print": true
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
          "name": "lancedbreader",
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
              { "name": "name", "type": "String" }
            ]
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": {
            "print": true
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
          "name": "lancedbreader",
          "parameter": {
            "mode": "cloud",
            "apiKey": "your_api_key",
            "database": "your_database",
            "table": "datax_test_table",
            "column": [
              { "name": "id", "type": "Int64" },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" },
              { "name": "embedding", "type": "FloatVector", "dimension": 4 }
            ],
            "filter": "id >= 0"
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": {
            "print": true
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
python $DATAX_HOME/bin/datax.py /tmp/job_lancedbreader.json
```

## 5. 注意事项

- column 中字段顺序对应输出列
- FloatVector 类型返回 JSON 数组字符串格式
- filter 使用 LanceDB SQL 语法
- local 模式产出/读取的是 Apache Arrow IPC File，不是 LanceDB 原生 .lance 文件，无法直接被 lancedb Python 本地库打开或查询
- local 模式仅支持单批次读写，不支持增量 append、向量索引、ANN 检索等 LanceDB 高级特性
- S3 模式通过 `s3://bucket/key` 格式的 uri 触发，同时需配置 `s3` 子对象提供连接信息，支持 MinIO / Ceph / 阿里云 OSS 等所有 S3 兼容存储

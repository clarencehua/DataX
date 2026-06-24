# S3Writer 使用指南

## 1. MinIO Docker 环境搭建

S3Reader/S3Writer 是 S3 兼容对象存储插件，本指南以 MinIO 为例。

```bash
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio:latest server /data --console-address ":9001"
```

## 2. 提交 DataX 任务

### 上传模式（mode=upload）

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "type": "long", "value": 999 },
              { "type": "string", "value": "s3_write_test" },
              { "type": "double", "value": 88.8 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "s3writer",
          "parameter": {
            "mode": "upload",
            "endpoint": "http://127.0.0.1:9000",
            "bucket": "test-bucket",
            "accessKey": "minioadmin",
            "secretKey": "minioadmin",
            "object": "output/result",
            "region": "us-east-1",
            "fieldDelimiter": ",",
            "encoding": "utf-8",
            "suffix": ".csv"
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

### 下载模式（mode=download）

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "s3reader",
          "parameter": {
            "endpoint": "http://127.0.0.1:9000",
            "bucket": "test-bucket",
            "accessKey": "minioadmin",
            "secretKey": "minioadmin",
            "prefix": "/data",
            "region": "us-east-1"
          }
        },
        "writer": {
          "name": "s3writer",
          "parameter": {
            "mode": "download",
            "endpoint": "http://127.0.0.1:9000",
            "bucket": "test-bucket",
            "accessKey": "minioadmin",
            "secretKey": "minioadmin",
            "destPath": "/tmp/s3_download",
            "region": "us-east-1"
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

### 运行

```bash
python datax/bin/datax.py job_s3writer.json
```

## 3. 参数说明

### 上传模式（mode=upload）

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | 是 | `download` | 设为 `upload` 启用上传模式 |
| `endpoint` | 是 | - | S3 兼容存储地址，格式 `http://host:port` |
| `bucket` | 是 | - | 存储桶名称 |
| `accessKey` | 是 | - | 访问密钥 |
| `secretKey` | 是 | - | 秘密密钥 |
| `object` | 是 | - | 写入对象 key 前缀，如 `output/result` |
| `region` | 否 | `us-east-1` | 区域 |
| `fieldDelimiter` | 否 | `,` | 列分隔符 |
| `encoding` | 否 | `utf-8` | 文件编码 |
| `nullFormat` | 否 | `null` | NULL 值表示 |
| `dateFormat` | 否 | - | 日期格式（如 `yyyy-MM-dd`） |
| `maxFileSize` | 否 | `1024` | 单个文件最大大小（MB） |
| `writeMode` | 否 | `truncate` | 写入模式：`truncate`（覆盖）/ `nonConflict`（不覆盖） |
| `suffix` | 否 | `""` | 文件后缀，如 `.csv` |

### 下载模式（mode=download）

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | 是 | `download` | 设为 `download` 启用下载模式 |
| `endpoint` | 是 | - | S3 兼容存储地址 |
| `bucket` | 是 | - | 存储桶名称 |
| `accessKey` | 是 | - | 访问密钥 |
| `secretKey` | 是 | - | 秘密密钥 |
| `destPath` | 是 | - | 下载目标目录（本地路径） |
| `region` | 否 | `us-east-1` | 区域 |

## 4. 注意事项

- S3Writer 有两种模式：`upload`（上传到 S3）和 `download`（从 S3 下载到本地）
- 上传时，实际写入的 key 为 `{object}{suffix}`，如 `output/result.csv`
- 超过 `maxFileSize` 会自动分片，key 变为 `{object}__1{suffix}`、`{object}__2{suffix}`...
- `writeMode=truncate` 会先删除同前缀的所有对象再写入
- `writeMode=nonConflict` 如果同前缀对象已存在则报错
- 下载模式配合 S3Reader 使用，reader 提供 key 列表，writer 下载到本地
- 适用于 MinIO、Ceph、阿里云 OSS 等 S3 兼容存储

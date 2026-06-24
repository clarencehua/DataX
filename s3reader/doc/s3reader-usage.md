# S3Reader 使用指南

## 1. MinIO Docker 环境搭建

S3Reader/S3Writer 是 S3 兼容对象存储插件，本指南以 MinIO 为例。

```bash
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio:latest server /data --console-address ":9001"
```

## 2. 创建测试数据

```bash
# 创建 bucket
docker exec minio sh -c 'mc alias set local http://localhost:9000 minioadmin minioadmin && mc mb local/test-bucket'

# 上传测试 CSV 文件
echo -e "id,name,score\n1,Alice,95.5\n2,Bob,87.0\n3,Charlie,91.3" | docker exec -i minio sh -c 'mc pipe local/test-bucket/data/test1.csv'
echo -e "id,name,score\n4,Diana,78.6\n5,Eve,88.9" | docker exec -i minio sh -c 'mc pipe local/test-bucket/data/test2.csv'
```

## 3. 提交 DataX 任务

### 配置示例

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
          "name": "streamwriter",
          "parameter": {
            "encoding": "UTF-8",
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

### 运行

```bash
python datax/bin/datax.py job_s3reader.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `endpoint` | 是 | - | S3 兼容存储地址，格式 `http://host:port` |
| `bucket` | 是 | - | 存储桶名称 |
| `accessKey` | 是 | - | 访问密钥 |
| `secretKey` | 是 | - | 秘密密钥 |
| `prefix` | 否 | - | 对象前缀（过滤路径），如 `/data` |
| `region` | 否 | `us-east-1` | 区域 |
| `fileType` | 否 | `[]` | 文件后缀过滤，如 `["csv","json"]` |

## 5. 注意事项

- S3Reader 只读取对象 **key 列表**（每行一个 key 字符串），不读取文件内容
- 返回的每行记录是一个对象完整 key（如 `data/test1.csv`）
- 支持 `fileType` 过滤，只返回指定后缀的对象
- `prefix` 支持 `/` 开头的路径，会自动去除
- 非递归读取：只列举 prefix 当前目录下的对象
- 适用于 MinIO、Ceph、阿里云 OSS 等 S3 兼容存储

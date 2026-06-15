# milvuswriter 使用文档

## 1. 功能说明
将数据写入 Milvus 集合。

## 2. 环境准备

### 2.1 Docker 启动 Milvus

使用 docker-compose 启动 Milvus 所需的 etcd、minio 和 milvus 服务：

```yaml
# /tmp/milvus-compose.yml
version: "3.5"

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    profiles:
      - donotstart

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/minio:/minio_data
    command: minio server /minio_data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
    profiles:
      - donotstart

  milvus:
    container_name: milvus
    image: milvusdb/milvus:v2.5.5
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
```

启动：
```bash
docker compose -f /tmp/milvus-compose.yml up -d
```

验证：
```bash
python3 -c "from pymilvus import MilvusClient; c = MilvusClient('http://localhost:19530'); print(c.list_collections()); c.close()"
```

## 3. 参数说明

| 参数名 | 必选 | 默认值 | 描述 |
|--------|------|--------|------|
| `endpoint` | 是 | 无 | Milvus 连接地址，如 `http://localhost:19530` |
| `username` | 否 | 空 | Milvus 用户名，与 token 二选一 |
| `password` | 否 | 空 | Milvus 密码 |
| `token` | 否 | 无 | Milvus token，与 username+password 二选一 |
| `database` | 否 | 空 | 数据库名，默认使用 default |
| `collection` | 是 | 无 | 目标集合名称 |
| `batchSize` | 否 | 100 | 批量提交记录数 |
| `schemaCreateMode` | 否 | `createIfNotExist` | 集合创建模式：`createIfNotExist`、`ignore`、`recreate` |
| `writeMode` | 否 | `upsert` | 写入模式：`insert`、`upsert` |
| `enableDynamicSchema` | 否 | 无 | 是否启用动态 schema |
| `connectTimeoutMs` | 否 | 10000 | 连接超时（毫秒） |
| `partition` | 否 | 无 | 分区名 |
| `column` | 是 | 无 | 字段定义列表 |

### column 字段属性

| 属性名 | 必选 | 描述 |
|--------|------|------|
| `name` | 是 | 字段名 |
| `type` | 是 | 数据类型，见下文支持的类型 |
| `primaryKey` | 否 | 是否为主键（Boolean），创建集合时必填 |
| `autoId` | 否 | 是否自动生成 ID（Boolean） |
| `dimension` | 否 | 向量维度，向量类型必填 |
| `maxLength` | 否 | VarChar 最大长度 |
| `maxCapacity` | 否 | Array 类型最大容量 |
| `elementType` | 否 | Array 元素类型 |
| `partitionKey` | 否 | 是否为分区键 |

### 支持的数据类型

`Bool`, `Int8`, `Int16`, `Int32`, `Int64`, `Float`, `Double`, `String`, `VarChar`,
`Array`, `JSON`, `BinaryVector`, `FloatVector`, `Float16Vector`, `BFloat16Vector`,
`SparseFloatVector`

## 4. 配置示例

### 4.1 streamreader → milvuswriter

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
              { "value": "Hello Milvus", "type": "string" },
              { "value": 3.14, "type": "double" },
              { "value": true, "type": "bool" }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "milvuswriter",
          "parameter": {
            "endpoint": "http://localhost:19530",
            "username": "",
            "password": "",
            "collection": "datax_test_collection",
            "schemaCreateMode": "recreate",
            "writeMode": "insert",
            "batchSize": 100,
            "column": [
              { "name": "id", "type": "Int64", "primaryKey": true },
              { "name": "vector", "type": "FloatVector", "dimension": 4 },
              { "name": "title", "type": "VarChar", "maxLength": 256 },
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

### 4.2 运行

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_milvuswriter.json
```

## 5. 数据验证

```python
from pymilvus import MilvusClient, connections, Collection

client = MilvusClient('http://localhost:19530')

# 创建索引（查询前需要）
index_params = {
    'field_name': 'vector',
    'metric_type': 'L2',
    'index_type': 'IVF_FLAT',
    'params': {'nlist': 128}
}
client.create_index('datax_test_collection', index_params)

client.load_collection('datax_test_collection')

# 查询
results = client.query(
    collection_name='datax_test_collection',
    filter='id >= 0',
    output_fields=['*'],
    limit=10
)
for r in results:
    print(r)
```

## 6. 注意事项

- 集合必须在查询前创建索引并加载
- `column` 中字段顺序必须与 reader 的输出列一一对应
- 使用 `recreate` 模式会删除已有集合重新创建
- `primaryKey` JSON key 对应 Java setter `setPrimaryKey`（而非 `isPrimaryKey`）
- Milvus 集群模式下需要 etcd + minio 支持

# CassandraWriter 使用文档

## 1. Cassandra Docker 环境搭建

```bash
# 启动 Cassandra 4.0 容器
docker run -d --name cassandra \
  -p 9042:9042 \
  cassandra:4.0

# 等待初始化完成（约 40s）
sleep 40

# 验证连接
docker exec cassandra cqlsh -e "SELECT release_version FROM system.local;"
```

## 2. 创建目标表

```bash
docker exec cassandra cqlsh -e "
CREATE KEYSPACE IF NOT EXISTS test_keyspace
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS test_keyspace.writer_tbl (
  id INT PRIMARY KEY,
  name TEXT,
  score FLOAT,
  active BOOLEAN,
  data TEXT
);
"
```

## 3. 获取容器 IP

```bash
docker inspect cassandra --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
# 输出示例: 172.17.0.2
```

## 4. 提交 DataX 任务

### 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "type": "long", "value": 10 },
              { "type": "string", "value": "TestWriter" },
              { "type": "double", "value": 99.9 },
              { "type": "bool", "value": true },
              { "type": "string", "value": "test_data" }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "cassandrawriter",
          "parameter": {
            "host": "127.0.0.1",
            "port": 9042,
            "keyspace": "test_keyspace",
            "table": "writer_tbl",
            "column": ["id", "name", "score", "active", "data"],
            "consistancyLevel": "LOCAL_QUORUM",
            "batchSize": 1
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
python datax/bin/datax.py job_cassandrawriter.json
```

## 5. 验证数据

```bash
docker exec cassandra cqlsh -e "SELECT * FROM test_keyspace.writer_tbl;"
```

预期输出 1 行写入的数据。

## 6. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `host` | 是 | - | Cassandra 节点地址，逗号分隔多个节点 |
| `port` | 否 | `9042` | Cassandra 原生 CQL 端口 |
| `username` | 否 | - | 认证用户名 |
| `password` | 否 | - | 认证密码 |
| `useSSL` | 否 | `false` | 是否启用 SSL |
| `keyspace` | 是 | - | 目标 keyspace |
| `table` | 是 | - | 目标表 |
| `column` | 是 | - | 写入的列名列表 |
| `consistancyLevel` | 否 | `LOCAL_QUORUM` | 一致性级别 |
| `batchSize` | 否 | `1` | 批量写入行数（UNLOGGED BATCH，最大 65535） |
| `asyncWrite` | 否 | `false` | 是否使用异步写入 |
| `connectionsPerHost` | 否 | `8` | 每主机连接数 |
| `maxPendingPerConnection` | 否 | `128` | 每连接最大待处理请求数 |

## 7. 写入模式

### 7.1 单条写入 (batchSize=1)
每条记录作为独立的 `INSERT` 执行，`session.execute()`。

### 7.2 批量写入 (batchSize>1, asyncWrite=false)
使用 `BatchStatement(Type.UNLOGGED)` 批量提交。如果批量失败，自动降级为逐条写入。

### 7.3 异步写入 (batchSize>1, asyncWrite=true)
使用 `session.executeAsync()` 并发写入，达到 batchSize 后等待所有 Future 完成。

## 8. 类型映射

| DataX 类型 | Cassandra 类型 |
|-----------|---------------|
| Long | INT, BIGINT, VARINT, SMALLINT, TINYINT |
| Double | FLOAT, DOUBLE, DECIMAL |
| String | ASCII, TEXT, VARCHAR, UUID, TIMEUUID, BLOB（HexString） |
| Bool | BOOLEAN |
| Date | DATE, TIMESTAMP |
| String (JSON) | LIST, MAP, SET, TUPLE, UDT |

## 9. 注意事项

- 不支持 `counter` 和 `custom` 类型
- BLOB 类型写入需传入 HexString（如 `"77726974655f64617461"`）
- `useSSL` 参数在配置中缺失时将正确默认为 `false`（DataX 1.0 修复）
- `writetime()` 可用作列名，用于设置写入时间戳

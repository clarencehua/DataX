# CassandraReader 使用文档

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

## 2. 创建测试数据

```bash
docker exec cassandra cqlsh -e "
CREATE KEYSPACE IF NOT EXISTS test_keyspace
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS test_keyspace.test_table (
  id INT PRIMARY KEY,
  name TEXT,
  score FLOAT,
  active BOOLEAN,
  data BLOB
);

INSERT INTO test_keyspace.test_table (id, name, score, active, data) VALUES (1, 'Alice', 95.5, true, textAsBlob('data1'));
INSERT INTO test_keyspace.test_table (id, name, score, active, data) VALUES (2, 'Bob', 87.0, false, textAsBlob('data2'));
INSERT INTO test_keyspace.test_table (id, name, score, active, data) VALUES (3, 'Charlie', 91.3, true, textAsBlob('data3'));
INSERT INTO test_keyspace.test_table (id, name, score, active, data) VALUES (4, 'Diana', 78.6, true, textAsBlob('data4'));
INSERT INTO test_keyspace.test_table (id, name, score, active, data) VALUES (5, 'Eve', 88.9, false, textAsBlob('data5'));
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
          "name": "cassandrareader",
          "parameter": {
            "host": "127.0.0.1",
            "port": 9042,
            "keyspace": "test_keyspace",
            "table": "test_table",
            "column": ["id", "name", "score", "active", "data"],
            "consistancyLevel": "LOCAL_QUORUM"
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
python datax/bin/datax.py job_cassandrareader.json
```

## 5. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `host` | 是 | - | Cassandra 节点地址，逗号分隔多个节点 |
| `port` | 否 | `9042` | Cassandra 原生 CQL 端口 |
| `username` | 否 | - | 认证用户名 |
| `password` | 否 | - | 认证密码 |
| `useSSL` | 否 | `false` | 是否启用 SSL |
| `keyspace` | 是 | - | 目标 keyspace |
| `table` | 是 | - | 目标表 |
| `column` | 是 | - | 读取的列名列表，支持 `writetime(col)` 语法 |
| `where` | 否 | - | CQL WHERE 过滤条件 |
| `allowFiltering` | 否 | `false` | 是否追加 ALLOW FILTERING |
| `consistancyLevel` | 否 | `LOCAL_QUORUM` | 一致性级别 |

## 6. 类型映射

| Cassandra 类型 | DataX 类型 |
|---------------|------------|
| ASCII, TEXT, VARCHAR | String |
| INT, BIGINT, VARINT, SMALLINT, TINYINT | Long |
| FLOAT, DOUBLE, DECIMAL | Double |
| BOOLEAN | Bool |
| BLOB | String（HexString） |
| DATE | Date |
| TIME, TIMESTAMP | Long（毫秒） |
| UUID, TIMEUUID | String |
| LIST, MAP, SET, TUPLE, UDT | String（JSON） |

## 7. 注意事项

- 不支持 `counter` 和 `custom` 类型
- `useSSL` 参数在配置中缺失时将正确默认为 `false`（DataX 1.0 修复）
- 支持 Murmur3Partitioner / RandomPartitioner 的 token 范围自动切分实现并发读取

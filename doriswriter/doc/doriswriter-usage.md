# DorisWriter 使用指南

## 1. Doris 数据库安装与启动（Docker）

详见 [dorisreader-usage.md](../dorisreader/doc/dorisreader-usage.md) 的「Doris 数据库安装与启动」章节。

简要步骤：

```bash
# 启动 FE
docker run -d --name doris-fe -e FE_SERVERS="fe1:172.17.0.2:9010" -e FE_ID="1" -p 9030:9030 -p 8030:8030 selectdb/doris.fe-ubuntu:2.0.4
sleep 50

FE_IP=$(docker inspect doris-fe --format '{{.NetworkSettings.IPAddress}}')

# 启动 BE
docker run -d --name doris-be -e HOST_TYPE=IP --entrypoint /opt/apache-doris/be_entrypoint.sh selectdb/doris.be-ubuntu:2.0.4 "$FE_IP"
sleep 50

# 设置密码
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -e "SET PASSWORD FOR 'root' = PASSWORD('root');"

# 创建目标表
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -proot -e "
CREATE DATABASE IF NOT EXISTS test_db;
CREATE TABLE IF NOT EXISTS test_db.writer_tbl (
    id INT,
    name VARCHAR(100),
    age INT,
    score DOUBLE
) DISTRIBUTED BY HASH(id) BUCKETS 1
PROPERTIES ('replication_num' = '1');
"
```

## 2. 提交 DataX 任务

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
              { "type": "long", "value": 999 },
              { "type": "string", "value": "test_write" },
              { "type": "long", "value": 20 },
              { "type": "double", "value": 88.8 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "doriswriter",
          "parameter": {
            "loadUrl": ["127.0.0.1:8030"],
            "column": ["id", "name", "age", "score"],
            "username": "root",
            "password": "root",
            "connection": [{
              "jdbcUrl": "jdbc:mysql://127.0.0.1:9030/test_db",
              "selectedDatabase": "test_db",
              "table": ["writer_tbl"]
            }]
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
python datax/bin/datax.py job_doriswriter.json
```

## 3. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `loadUrl[]` | 是 | - | FE 的 HTTP 地址列表，格式 `"host:http_port"` |
| `username` | 是 | - | 数据库用户名 |
| `password` | 否 | 空 | 数据库密码 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl` | 是 | - | JDBC 连接串，用于 preSql/postSql，格式 `jdbc:mysql://host:9030/db` |
| `connection.selectedDatabase` | 是 | - | 目标数据库名 |
| `connection.table[]` | 是 | - | 目标表名列表 |
| `column` | 是 | - | 写入的列名列表 |
| `preSql` | 否 | - | 写入前执行的 SQL（通过 JDBC 执行） |
| `postSql` | 否 | - | 写入后执行的 SQL |
| `maxBatchRows` | 否 | `500000` | 每批次最大行数 |
| `batchSize` | 否 | `94371840` (90MB) | 每批次最大字节数 |
| `flushInterval` | 否 | `30000` (30s) | 定时刷入间隔（毫秒） |
| `labelPrefix` | 否 | `datax_doris_writer_` | Stream Load label 前缀，用于去重 |
| `loadProps` | 否 | - | Stream Load 请求参数，如 `{"format": "json", "strip_outer_array": true}` |

## 4. 注意事项

- DorisWriter 使用 **Stream Load**（HTTP PUT）写入数据，不走 JDBC
- `jdbcUrl` 在 writer 中配置为字符串格式 `jdbc:mysql://host:9030/db`
- 默认使用 **CSV** 格式，列分隔符为 `\t`，行分隔符为 `\n`
- 可通过 `loadProps` 切换为 JSON 格式：
  ```json
  "loadProps": {
    "format": "json",
    "strip_outer_array": true
  }
  ```
- Stream Load 的 HTTP 端口是 FE 的 `http_port`（默认 `8030`），**不是** MySQL 端口
- 目标表需预先创建，且表列数与 `column` 配置匹配
- Doris 建表时需指定 `DISTRIBUTED BY HASH` 和 `"replication_num" = "1"`（单副本）

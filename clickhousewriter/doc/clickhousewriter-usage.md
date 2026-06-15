# ClickhouseWriter 使用文档

## 1. ClickHouse Docker 环境搭建

```bash
# 启动 ClickHouse 容器（设置密码）
docker run -d --name clickhouse-server \
  -p 8123:8123 -p 9000:9000 \
  -e CLICKHOUSE_USER=default \
  -e CLICKHOUSE_PASSWORD=your_password \
  clickhouse/clickhouse-server:24.8

# 验证连接
docker exec clickhouse-server clickhouse-client --password your_password --query "SELECT 1"
```

## 2. 创建目标表

```bash
docker exec clickhouse-server clickhouse-client --password your_password --query "
CREATE DATABASE IF NOT EXISTS test_db;
CREATE TABLE IF NOT EXISTS test_db.writer_tbl (
    id Int32,
    name String,
    score Float64
) ENGINE = MergeTree()
ORDER BY id;
"
```

## 3. 获取容器 IP

```bash
docker inspect clickhouse-server --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
# 输出示例: 172.17.0.4
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
              {
                "value": 100,
                "type": "long"
              },
              {
                "value": "datax_test",
                "type": "string"
              },
              {
                "value": 88.5,
                "type": "double"
              }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "clickhousewriter",
          "parameter": {
            "username": "default",
            "password": "your_password",
            "column": ["id", "name", "score"],
            "connection": [
              {
                "jdbcUrl": ["jdbc:clickhouse://<clickhouse_ip>:8123/test_db"],
                "table": ["writer_tbl"]
              }
            ],
            "writeMode": "insert",
            "batchSize": 1024
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

### 运行命令

```bash
python $DATAX_HOME/bin/datax.py job.json
```

## 5. 参数说明

| 参数名 | 是否必填 | 说明 |
|--------|----------|------|
| `jdbcUrl` | 是 | JDBC 连接 URL，格式 `jdbc:clickhouse://host:port/database`，必须为数组 |
| `username` | 是 | ClickHouse 用户名（默认 `default`） |
| `password` | 是 | ClickHouse 密码 |
| `table` | 是 | 要写入的表名，数组格式 |
| `column` | 是 | 要写入的列名数组，需与表结构匹配 |
| `writeMode` | 否 | 写入模式，仅支持 `insert`（默认值） |
| `batchSize` | 否 | 每次批量写入行数，默认 65536 |
| `batchByteSize` | 否 | 每次批量写入字节数，默认 134217728 |
| `dryRun` | 否 | 预检查模式，只检查连接和列信息，不实际写入，默认 false |
| `preSql` | 否 | 写入前执行的 SQL 语句 |
| `postSql` | 否 | 写入后执行的 SQL 语句 |
| `session` | 否 | Session 配置 |

## 6. 类型映射

| DataX 内部类型 | ClickHouse 数据类型 |
|---------------|-------------------|
| Long | UInt8/16/32/64/128/256, Int8/16/32/64/128/256 |
| Double | Float32, Float64, Decimal |
| String | String, FixedString |
| Date | DATE, Date32, DateTime, DateTime64 |
| Boolean | Boolean |
| Bytes | BLOB, BFILE, RAW, LONG RAW |

## 7. 验证结果

```bash
docker exec clickhouse-server clickhouse-client --password your_password \
  --query "SELECT * FROM test_db.writer_tbl"
```

预期输出：
```
100	datax_test	88.5
```

任务日志尾部：
```
任务启动时刻                    : 2026-06-15 14:14:56
任务结束时刻                    : 2026-06-15 14:15:07
读出记录总数                    :                   1
读写失败总数                    :                   0
```

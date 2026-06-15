# ClickhouseReader 使用文档

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

## 2. 创建测试数据

```bash
docker exec clickhouse-server clickhouse-client --password your_password --query "
CREATE DATABASE IF NOT EXISTS test_db;
CREATE TABLE IF NOT EXISTS test_db.test_tbl (
    id Int32,
    name String,
    score Float64
) ENGINE = MergeTree()
ORDER BY id;
INSERT INTO test_db.test_tbl VALUES (1, 'Alice', 95.5), (2, 'Bob', 87.0), (3, 'Charlie', 91.3);
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
          "name": "clickhousereader",
          "parameter": {
            "username": "default",
            "password": "your_password",
            "column": ["id", "name", "score"],
            "connection": [
              {
                "jdbcUrl": ["jdbc:clickhouse://<clickhouse_ip>:8123/test_db"],
                "table": ["test_tbl"]
              }
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
| `table` | 是 | 要读取的表名，数组格式 |
| `column` | 是 | 要读取的列名，可使用 `["*"]` 读取所有列 |
| `where` | 否 | WHERE 条件 |
| `querySql` | 否 | 自定义查询 SQL（设置后将忽略 table/column/where） |
| `splitPk` | 否 | 分片主键，用于并发读取 |
| `fetchSize` | 否 | 每次批量读取行数，默认 1024 |
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

成功后输出如下：

```
2026-06-15 06:14:27.682 [0-0-0-reader] INFO CommonRdbmsReader$Task - Begin to read record by Sql: [select * from test_tbl] jdbcUrl:[jdbc:clickhouse://172.17.0.4:8123/test_db].
1	Alice	95.5
2	Bob	87
3	Charlie	91.3
...
任务启动时刻                    : 2026-06-15 14:14:27
任务结束时刻                    : 2026-06-15 14:14:37
读出记录总数                    :                   5
读写失败总数                    :                   0
```

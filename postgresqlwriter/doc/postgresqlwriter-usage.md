# PostgresqlWriter 使用文档


## 1. PostgreSQL Docker 环境搭建

```bash
docker run -d --name postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=datax \
  -e POSTGRES_PASSWORD=datax123 \
  -e POSTGRES_DB=datax_test \
  postgres:16
```

## 2. 创建目标表

```bash
docker exec -i postgres psql -U datax -d datax_test -c \
  "CREATE TABLE writer_tbl (id INT, name VARCHAR(50), score DOUBLE PRECISION);"
```

## 3. 提交 DataX 任务

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
              { "type": "string", "value": "pg_write_test" },
              { "type": "double", "value": 88.8 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "postgresqlwriter",
          "parameter": {
            "username": "datax",
            "password": "datax123",
            "column": ["id", "name", "score"],
            "connection": [
              {
                "jdbcUrl": "jdbc:postgresql://127.0.0.1:5432/datax_test",
                "table": ["writer_tbl"]
              }
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

### 运行

```bash
python datax/bin/datax.py job_postgresqlwriter.json
```

## 4. 验证数据

```bash
docker exec -i postgres psql -U datax -d datax_test -c \
  "SELECT * FROM writer_tbl;"
```

## 5. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `username` | 是 | - | 数据库用户名 |
| `password` | 是 | - | 数据库密码 |
| `column` | 是 | - | 写入列名数组 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl` | 是 | - | JDBC 连接串 |
| `connection.table[]` | 是 | - | 表名数组 |
| `preSql` | 否 | - | 写入前执行的 SQL 数组 |
| `postSql` | 否 | - | 写入后执行的 SQL 数组 |
| `batchSize` | 否 | `2048` | 每批插入行数 |
| `dryRun` | 否 | `false` | 预检查模式 |

## 6. 注意事项

- PostgreSQL 仅支持 `INSERT` 模式，不支持 `writeMode` 参数
- Writer 支持自动类型转换，`serial`/`bigserial` 类型使用 `?::int`/`?::int8` 占位符

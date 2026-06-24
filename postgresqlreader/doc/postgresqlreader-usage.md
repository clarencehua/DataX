# PostgresqlReader 使用文档

## 1. PostgreSQL Docker 环境搭建

```bash
docker run -d --name postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=datax \
  -e POSTGRES_PASSWORD=datax123 \
  -e POSTGRES_DB=datax_test \
  postgres:16
```

## 2. 创建测试数据

```bash
docker exec -i postgres psql -U datax -d datax_test << 'EOF'
CREATE TABLE reader_tbl (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50),
    score DOUBLE PRECISION,
    active BOOLEAN
);
INSERT INTO reader_tbl (name, score, active) VALUES
    ('Alice', 95.5, true),
    ('Bob', 87.0, false),
    ('Charlie', 91.3, true),
    ('Diana', 78.6, true),
    ('Eve', 88.9, false);
EOF
```

## 3. 提交 DataX 任务

### 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "postgresqlreader",
          "parameter": {
            "username": "datax",
            "password": "datax123",
            "connection": [
              {
                "jdbcUrl": ["jdbc:postgresql://127.0.0.1:5432/datax_test"],
                "table": ["reader_tbl"]
              }
            ],
            "column": ["id", "name", "score", "active"],
            "where": "score > 80.0"
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
python datax/bin/datax.py job_postgresqlreader.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `username` | 是 | - | 数据库用户名 |
| `password` | 是 | - | 数据库密码 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl[]` | 是 | - | JDBC 连接串数组，格式 `jdbc:postgresql://host:port/database` |
| `connection.table[]` | 是 | - | 表名数组，支持 `schema.table` 格式 |
| `column` | 是 | `["*"]` | 读取的列名数组，支持 `"*"` |
| `where` | 否 | - | 筛选条件（不含 `WHERE` 关键字） |
| `splitPk` | 否 | - | 切分键，用于并发读取 |
| `fetchSize` | 否 | `1000` | 每次读取的行数 |
| `session` | 否 | - | 会话设置（PostgreSQL 特有参数） |
| `querySql` | 否 | - | 自定义 SQL 查询（使用后忽略 `table`/`column`/`where`） |
| `mandatoryEncoding` | 否 | - | 强制编码 |

## 5. 注意事项

- `jdbcUrl` 在 reader 中必须配置为数组（如 `["jdbc:postgresql://..."]`）
- 支持 Schema：表名使用 `schema.table` 格式
- `splitPk` 建议使用主键或有索引的列以提高切分效率

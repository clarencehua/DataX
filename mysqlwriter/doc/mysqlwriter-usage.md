# MysqlWriter 使用指南

## 1. MySQL Docker 环境搭建

```bash
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=datax_test \
  mysql:8.0
```

## 2. 创建目标表

```bash
docker exec mysql mysql -uroot -proot -e "
USE datax_test;
CREATE TABLE IF NOT EXISTS writer_tbl (
    id INT,
    name VARCHAR(50),
    age INT,
    score DOUBLE
);
"
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
              { "type": "string", "value": "mysql_write_test" },
              { "type": "long", "value": 20 },
              { "type": "double", "value": 88.8 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "mysqlwriter",
          "parameter": {
            "writeMode": "insert",
            "username": "root",
            "password": "root",
            "column": ["id", "name", "age", "score"],
            "connection": [{
              "jdbcUrl": "jdbc:mysql://127.0.0.1:3306/datax_test",
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
python datax/bin/datax.py job_mysqlwriter.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `writeMode` | 是 | `insert` | 写入模式：`insert`、`replace`、`update` |
| `username` | 是 | - | 数据库用户名 |
| `password` | 是 | - | 数据库密码 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl` | 是 | - | JDBC 连接串，格式 `jdbc:mysql://host:3306/db` |
| `connection.table[]` | 是 | - | 目标表名列表 |
| `column` | 是 | - | 写入的列名列表 |
| `session` | 否 | - | 会话设置（如 `set session sql_mode='ANSI'`） |
| `preSql` | 否 | - | 写入前执行的 SQL |
| `postSql` | 否 | - | 写入后执行的 SQL |
| `batchSize` | 否 | `1024` | 一次性批量提交的记录数 |

## 5. 注意事项

- `jdbcUrl` 在 writer 中配置为字符串格式 `jdbc:mysql://host:3306/db`
- DataX 会自动在 jdbcUrl 后追加：`yearIsDateType=false&zeroDateTimeBehavior=convertToNull&rewriteBatchedStatements=true`
- 如需指定编码，可在 jdbcUrl 后追加：`useUnicode=true&characterEncoding=utf8`
- 目标表需预先创建，且表列数与 `column` 配置匹配
- 目的表所在数据库必须是主库才能写入数据
- `writeMode` 支持三种模式：
  - `insert`：主键冲突时写不进去
  - `replace`：主键冲突时替换整行
  - `update`：主键冲突时更新

# MysqlReader 使用指南

## 1. MySQL Docker 环境搭建

```bash
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=datax_test \
  mysql:8.0
```

## 2. 创建测试数据

```bash
docker exec mysql mysql -uroot -proot -e "
USE datax_test;
CREATE TABLE reader_tbl (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50),
    score DOUBLE,
    active TINYINT(1)
);
INSERT INTO reader_tbl (name, score, active) VALUES
    ('Alice', 95.5, 1),
    ('Bob', 87.0, 0),
    ('Charlie', 91.3, 1),
    ('Diana', 78.6, 1),
    ('Eve', 88.9, 0);
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
          "name": "mysqlreader",
          "parameter": {
            "username": "root",
            "password": "root",
            "column": ["id", "name", "score", "active"],
            "where": "score > 80.0",
            "connection": [{
              "table": ["reader_tbl"],
              "jdbcUrl": ["jdbc:mysql://127.0.0.1:3306/datax_test"]
            }]
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

### 自定义 SQL 读取

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "mysqlreader",
          "parameter": {
            "username": "root",
            "password": "root",
            "connection": [{
              "querySql": ["select id, name from reader_tbl where score > 80"],
              "jdbcUrl": ["jdbc:mysql://127.0.0.1:3306/datax_test"]
            }]
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
python datax/bin/datax.py job_mysqlreader.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `username` | 是 | - | 数据库用户名 |
| `password` | 是 | - | 数据库密码 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl[]` | 是 | - | JDBC 连接串数组，格式 `jdbc:mysql://host:3306/db` |
| `connection.table[]` | 是 | - | 表名数组（`table` 与 `querySql` 二选一） |
| `connection.querySql[]` | 否 | - | 自定义查询 SQL（与 `table` 二选一） |
| `column` | 是 | `["*"]` | 读取的列名数组（仅 `table` 模式下必填） |
| `where` | 否 | - | 筛选条件（仅 `table` 模式，不含 `WHERE` 关键字） |
| `splitPk` | 否 | - | 切分键，用于并发读取（仅支持整型） |

## 5. 注意事项

- `jdbcUrl` 在 reader 中必须配置为数组（如 `["jdbc:mysql://..."]`）
- `table` 和 `querySql` **互斥**
- `splitPk`、`where` 仅在 `table` 模式下生效
- 支持 Schema：表名使用 `schema.table` 格式
- `splitPk` 建议使用主键或有索引的列以提高切分效率

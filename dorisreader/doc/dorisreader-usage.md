# DorisReader 使用指南

## 1. Doris 数据库安装与启动（Docker）

```bash
# 启动 FE
docker run -d --name doris-fe \
  -e FE_SERVERS="fe1:172.17.0.2:9010" \
  -e FE_ID="1" \
  -p 9030:9030 -p 8030:8030 \
  selectdb/doris.fe-ubuntu:2.0.4

# 等待 FE 就绪
sleep 50

FE_IP=$(docker inspect doris-fe --format '{{.NetworkSettings.IPAddress}}')

# 启动 BE
docker run -d --name doris-be \
  -e HOST_TYPE=IP \
  --entrypoint /opt/apache-doris/be_entrypoint.sh \
  selectdb/doris.be-ubuntu:2.0.4 \
  "$FE_IP"

# 等待 BE 就绪
sleep 50

# 设置 root 密码
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot \
  -e "SET PASSWORD FOR 'root' = PASSWORD('root');"
```

## 2. 创建测试数据

```bash
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -proot -e "
CREATE DATABASE IF NOT EXISTS test_db;
CREATE TABLE IF NOT EXISTS test_db.reader_tbl (
    id INT,
    name VARCHAR(100),
    age INT,
    score DOUBLE
) DISTRIBUTED BY HASH(id) BUCKETS 1
PROPERTIES ('replication_num' = '1');
"

docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -proot -e "
INSERT INTO test_db.reader_tbl VALUES
    (1, 'Alice', 25, 92.5),
    (2, 'Bob', 30, 85.0),
    (3, 'Charlie', 35, 78.5),
    (4, 'Diana', 28, 95.0),
    (5, 'Eve', 32, 88.5);
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
          "name": "dorisreader",
          "parameter": {
            "username": "root",
            "password": "root",
            "column": ["id", "name", "age", "score"],
            "where": "age > 25",
            "splitPk": "id",
            "connection": [{
              "table": ["reader_tbl"],
              "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"]
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
          "name": "dorisreader",
          "parameter": {
            "username": "root",
            "password": "root",
            "connection": [{
              "querySql": ["select id, name from reader_tbl where age > 18"],
              "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"]
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
python datax/bin/datax.py job_dorisreader.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `username` | 是 | - | 数据库用户名 |
| `password` | 是 | - | 数据库密码 |
| `connection` | 是 | - | 连接信息数组 |
| `connection.jdbcUrl[]` | 是 | - | JDBC 连接串数组，格式 `jdbc:mysql://host:9030/db` |
| `connection.table[]` | 是 | - | 表名数组（`table` 与 `querySql` 二选一） |
| `connection.querySql[]` | 否 | - | 自定义查询 SQL（与 `table` 二选一） |
| `column` | 是 | `["*"]` | 读取的列名数组（仅 `table` 模式下必填） |
| `where` | 否 | - | 筛选条件（仅 `table` 模式，不含 `WHERE` 关键字） |
| `splitPk` | 否 | - | 切分键，用于并发读取（仅支持整型） |
| `fetchSize` | 否 | `Integer.MIN_VALUE` | 每批拉取条数 |
| `session` | 否 | - | 会话设置 |
| `preSql` | 否 | - | 读取前执行的 SQL |
| `postSql` | 否 | - | 读取后执行的 SQL |

## 5. 注意事项

- DorisReader 底层通过 MySQL JDBC 驱动连接 Doris（`jdbc:mysql://...`）
- `jdbcUrl` 在 reader 中必须配置为数组（如 `["jdbc:mysql://..."]`）
- `table` 和 `querySql` **互斥**
- `splitPk`、`where` 仅在 `table` 模式下生效
- Doris 建表时必须指定 `DISTRIBUTED BY HASH` 和 `PROPERTIES ("replication_num" = "1")`（单副本测试）

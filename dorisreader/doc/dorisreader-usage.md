# DorisReader 使用指南

## Doris 数据库安装与启动（Docker）

```bash
# 启动 FE（Frontend）
docker run -d --name doris-fe \
  -e FE_SERVERS="fe1:172.17.0.2:9010" \
  -e FE_ID="1" \
  -p 9030:9030 -p 8030:8030 \
  selectdb/doris.fe-ubuntu:2.0.4

# 等待 FE 就绪（约 40s）
sleep 40

# 启动 BE（Backend）
FE_IP=$(docker inspect doris-fe --format '{{.NetworkSettings.IPAddress}}')
docker run -d --name doris-be \
  -e HOST_TYPE=IP \
  --entrypoint /opt/apache-doris/be_entrypoint.sh \
  selectdb/doris.be-ubuntu:2.0.4 \
  "$FE_IP"

# 等待 BE 就绪
sleep 40

# 清理重复注册的 BE（如有）
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot \
  -e "SHOW BACKENDS;" 2>/dev/null | awk 'NR>1 && $2!~/^[0-9]/ {print $2}' | while read h; do
  docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot \
    -e "ALTER SYSTEM DROPP BACKEND '${h}:9050';"
done
```

连接信息：
| 参数 | 值 |
|---|---|
| MySQL 协议端口 | `9030` |
| FE HTTP 端口 | `8030` |
| JDBC URL | `jdbc:mysql://127.0.0.1:9030/{database}` |
| 默认用户 | `root`（密码需手动设置） |

### 创建测试数据

```bash
# 设置 root 密码
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot \
  -e "SET PASSWORD FOR 'root' = PASSWORD('root');"

# 建库建表
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -proot -e "
CREATE DATABASE test_db;
CREATE TABLE test_db.test_tbl (
    id INT,
    name VARCHAR(100),
    age INT,
    score DOUBLE
) DISTRIBUTED BY HASH(id) BUCKETS 1
PROPERTIES ('replication_num' = '1');
"

# 插入数据
docker exec doris-fe mysql -h127.0.0.1 -P9030 -uroot -proot -e "
INSERT INTO test_db.test_tbl VALUES (1, 'Alice', 25, 92.5);
INSERT INTO test_db.test_tbl VALUES (2, 'Bob', 30, 85.0);
INSERT INTO test_db.test_tbl VALUES (3, 'Charlie', 35, 78.5);
INSERT INTO test_db.test_tbl VALUES (4, 'Diana', 28, 95.0);
INSERT INTO test_db.test_tbl VALUES (5, 'Eve', 32, 88.5);
"
```

## 配置样例

### 1. 按表读取

```json
{
  "name": "dorisreader",
  "parameter": {
    "username": "root",
    "password": "root",
    "column": ["id", "name", "age"],
    "where": "age > 18",
    "splitPk": "id",
    "connection": [{
      "table": ["test_tbl"],
      "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"]
    }]
  }
}
```

### 2. 自定义 SQL 读取

```json
{
  "name": "dorisreader",
  "parameter": {
    "username": "root",
    "password": "root",
    "connection": [{
      "querySql": ["select id, name from test_tbl where age > 18"],
      "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"]
    }]
  }
}
```

## 参数说明

### 必填参数

| 参数 | 说明 |
|---|---|
| `username` | 数据库用户名 |
| `password` | 密码（不可为空） |
| `connection[].jdbcUrl[]` | JDBC 连接串，**数组格式**，如 `["jdbc:mysql://host:9030/db"]` |
| `connection[].table[]` | 表名列表（`table` 与 `querySql` 二选一） |
| `connection[].querySql[]` | 自定义查询 SQL（与 `table` 二选一） |
| `column[]` | 列名列表，可用 `["*"]`（仅 `table` 模式下必填） |

### 可选参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `where` | 无 | WHERE 条件（仅 `table` 模式） |
| `splitPk` | 无 | 分片主键，仅支持整型；用于并发读取加速 |
| `fetchSize` | `Integer.MIN_VALUE` | 每批拉取条数；默认值会让 MySQL JDBC 驱动流式读取 |
| `session[]` | 无 | 会话配置 |
| `preSql[]` | 无 | 读取前执行的 SQL |
| `postSql[]` | 无 | 读取后执行的 SQL |

### 注意事项

- DorisReader 底层通过 MySQL JDBC 驱动连接 Doris（`jdbc:mysql://...`）
- `table` 和 `querySql` **互斥**
- `splitPk`、`where` 仅在 `table` 模式下生效
- Doris 建表时必须指定 `DISTRIBUTED BY HASH` 和 `PROPERTIES ("replication_num" = "1")`（单副本测试）

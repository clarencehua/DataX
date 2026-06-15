# OracleReader 使用指南

## Oracle 数据库安装与启动

### Docker 方式（推荐）

```bash
# 拉取 Oracle XE 21c 镜像（轻量免费版，约 3GB）
docker pull gvenzl/oracle-xe:21-faststart

# 启动容器（默认 SID: XE, 密码: test123）
docker run -d --name oracle-test \
  -e ORACLE_PASSWORD=test123 \
  -p 1521:1521 \
  gvenzl/oracle-xe:21-faststart

# 查看启动日志，等待数据库就绪
docker logs -f oracle-test
# 看到 "Completed: ALTER DATABASE OPEN" 即就绪
```

连接信息：
| 参数 | 值 |
|---|---|
| Host | `localhost` |
| Port | `1521` |
| SID | `XE` |
| User | `system` |
| Password | `test123` |

### 创建测试用户和数据

```bash
# 创建用户
docker exec oracle-test bash -c 'cat <<EOSQL | sqlplus -S system/test123@XE
CREATE USER datax_test IDENTIFIED BY datax_test;
GRANT CONNECT, RESOURCE TO datax_test;
ALTER USER datax_test QUOTA UNLIMITED ON USERS;
EOSQL'

# 创建表并插入数据
docker exec oracle-test bash -c 'cat <<EOSQL | sqlplus -S datax_test/datax_test@XE
CREATE TABLE test_tbl (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(100),
    age NUMBER,
    created_date DATE
);
INSERT INTO test_tbl VALUES (1, '\''Alice'\'', 25, SYSDATE);
INSERT INTO test_tbl VALUES (2, '\''Bob'\'', 30, SYSDATE);
INSERT INTO test_tbl VALUES (3, '\''Charlie'\'', 35, SYSDATE);
INSERT INTO test_tbl VALUES (4, '\''Diana'\'', 28, SYSDATE);
INSERT INTO test_tbl VALUES (5, '\''Eve'\'', 32, SYSDATE);
COMMIT;
EOSQL'

# 验证数据
docker exec oracle-test bash -c 'echo "SELECT * FROM datax_test.test_tbl;" | sqlplus -S datax_test/datax_test@XE'
```

### 编译 DataX

```bash
cd /code/DataX
mvn clean install -DskipTests
mvn assembly:single -Passembly -DskipTests
```

### 运行 DataX 任务

```bash
python /code/DataX/target/datax-all-in-one/datax/bin/datax.py /path/to/job.json
```

## 配置样例

### 1. 按表读取

```json
{
  "name": "oraclereader",
  "parameter": {
    "username": "user",
    "password": "pass",
    "column": ["id", "name", "age"],
    "where": "age > 18",
    "splitPk": "id",
    "connection": [{
      "table": ["test_tbl"],
      "jdbcUrl": ["jdbc:oracle:thin:@host:1521:XE"]
    }]
  }
}
```

### 2. 自定义 SQL 读取

```json
{
  "name": "oraclereader",
  "parameter": {
    "username": "user",
    "password": "pass",
    "connection": [{
      "querySql": ["select id, name from test_tbl where age > 18"],
      "jdbcUrl": ["jdbc:oracle:thin:@host:1521:XE"]
    }]
  }
}
```

## 参数说明

### 必填参数

| 参数 | 说明 |
|---|---|
| `username` | 数据库用户名 |
| `password` | 密码 |
| `connection[].jdbcUrl[]` | JDBC 连接串，**数组格式**，如 `["jdbc:oracle:thin:@host:1521:XE"]` |
| `connection[].table[]` | 表名列表（`table` 与 `querySql` 二选一） |
| `connection[].querySql[]` | 自定义查询 SQL（与 `table` 二选一） |
| `column[]` | 列名列表，可用 `["*"]` 表示所有列（仅 `table` 模式下必填） |

### 可选参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `where` | 无 | WHERE 条件，如 `"age > 18"`（仅 `table` 模式） |
| `splitPk` | 无 | 分片主键，用于并发读取加速；仅支持整型和字符串类型 |
| `splitFactor` | `5` | 分片倍数，`分片数 = ceil(通道数 / 表数) * splitFactor` |
| `samplePercentage` | `0.1` | Oracle 随机采样分片的采样比例 |
| `fetchSize` | `1024` | 每次批量拉取的数据条数，过大(>2048)可能 OOM |
| `hint` | 无 | Oracle SQL hint，如 `"test_tbl#parallel(a,4)"`（仅 `table` 模式） |
| `session[]` | 无 | 会话配置，如 `["alter session set NLS_DATE_FORMAT='yyyy-mm-dd hh24:mi:ss'"]` |
| `preSql[]` | 无 | 读取前执行的 SQL |
| `postSql[]` | 无 | 读取后执行的 SQL |
| `mandatoryEncoding` | 无 | 强制字符编码转换 |

### 注意事项

- `table` 和 `querySql` **互斥**，只能选一种
- `splitPk`、`where`、`hint` 仅在 `table` 模式下生效
- `jdbcUrl` 须为**数组格式**（即使只填一个）

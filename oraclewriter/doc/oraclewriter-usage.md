# OracleWriter 使用指南

## Oracle 数据库安装与启动

详见 [oraclereader-usage.md](../oraclereader/doc/oraclereader-usage.md) 的「Oracle 数据库安装与启动」章节。

简要步骤：

```bash
# 启动 Oracle
docker run -d --name oracle-test -e ORACLE_PASSWORD=test123 -p 1521:1521 gvenzl/oracle-xe:21-faststart

# 创建测试表
docker exec oracle-test bash -c 'cat <<EOSQL | sqlplus -S datax_test/datax_test@XE
CREATE TABLE writer_tbl (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(100),
    age NUMBER
);
EOSQL'

# 编译 DataX
cd /code/DataX && mvn clean install -DskipTests && mvn assembly:single -Passembly -DskipTests

# 运行 DataX
python /code/DataX/target/datax-all-in-one/datax/bin/datax.py /path/to/job.json
```

## 配置样例

```json
{
  "name": "oraclewriter",
  "parameter": {
    "username": "user",
    "password": "pass",
    "column": ["id", "name", "age"],
    "preSql": ["delete from @table"],
    "batchSize": 1024,
    "connection": [{
      "table": ["writer_tbl"],
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
| `connection[].jdbcUrl` | JDBC 连接串，**数组格式**，如 `["jdbc:oracle:thin:@host:1521:XE"]` |
| `connection[].table[]` | 目标表名列表 |
| `column[]` | 写入的列名列表，不可为空；可用 `["*"]` 表示所有列 |

### 可选参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `preSql[]` | 无 | 写入前执行 SQL，可用 `@table` 占位符表示当前表名 |
| `postSql[]` | 无 | 写入后执行 SQL，可用 `@table` 占位符表示当前表名 |
| `batchSize` | `2048` | 批量提交行数，推荐 100-1000 |
| `batchByteSize` | `33554432` (32MB) | 批量提交字节数阈值 |
| `emptyAsNull` | `true` | 空字符串是否转为 NULL |
| `session[]` | 无 | 会话配置 |
| `writeMode` | **不支持** | OracleWriter **不支持**此参数，配置会报错 |
| `dryRun` | `false` | 是否跳过重试逻辑 |

### 注意事项

- **不支持 `writeMode` 参数**（Oracle 仅支持 INSERT）
- `jdbcUrl` 须为**数组格式**（与 oraclereader 一致）
- `column` 中列数必须与来源记录列数一致，否则运行时报错
- `preSql` 中 `@table` 会在执行时替换为实际表名，适合多表写入场景
- OracleWriter 与 MySQLWriter 不同，不支持 `replace` / `update` 写入模式

# tdenginewriter 使用文档

## 1. TDengine Docker 环境搭建

```bash
# 启动 TDengine 容器（REST 端口 6041）
docker run -d --name tdengine \
  -p 6041:6041 \
  tdengine/tdengine:3.3.0.3

# 验证连接
docker exec tdengine taos -s "SELECT 1"
```

## 2. 创建目标表

```bash
docker exec tdengine taos -s "
CREATE DATABASE IF NOT EXISTS testdb;
USE testdb;
CREATE STABLE IF NOT EXISTS writer_stb (
  ts TIMESTAMP,
  temperature FLOAT,
  humidity INT,
  device_id BINARY(20),
  location NCHAR(20)
) TAGS (model BINARY(10));
"
```

## 3. 获取容器 IP

```bash
docker inspect tdengine --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
# 输出示例: 172.17.0.2
```

## 4. 提交 DataX 任务（写入超表）

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
              { "type": "string", "value": "sub_tb_1" },
              { "type": "date", "value": "2024-06-16 12:00:01" },
              { "type": "double", "value": 36.5 },
              { "type": "long", "value": 60 },
              { "type": "string", "value": "sensor_01" },
              { "type": "string", "value": "Beijing" }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "tdenginewriter",
          "parameter": {
            "username": "root",
            "password": "taosdata",
            "column": [
              "tbname",
              "ts",
              "temperature",
              "humidity",
              "device_id",
              "location"
            ],
            "connection": [
              {
                "table": ["writer_stb"],
                "jdbcUrl": "jdbc:TAOS-RS://127.0.0.1:6041/testdb"
              }
            ],
            "batchSize": 100,
            "ignoreTagsUnmatched": true
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
python datax/bin/datax.py job_tdenginewriter.json
```

## 5. 验证数据

```bash
docker exec tdengine taos -s "SELECT * FROM testdb.sub_tb_1;"
```

预期输出 1 行数据。

## 6. 参数说明

| 参数 | 必须 | 说明 |
|------|------|------|
| `username` | 是 | TDengine 用户名，默认 `root` |
| `password` | 是 | TDengine 密码 |
| `jdbcUrl` | 是 | JDBC 连接地址，格式 `jdbc:TAOS-RS://host:port/db`（数据库名须在 URL 中） |
| `table` | 是 | 目标表名列表（超表或普通表） |
| `column` | 是 | 写入的列名列表。写入超表时包含 `tbname` 列将自动创建/写入子表 |
| `batchSize` | 否 | 批量提交行数，默认 100 |
| `ignoreTagsUnmatched` | 否 | 写入子表时是否忽略标签不匹配的行，默认 false |

## 7. 写入模式

### 7.1 写入超表（含 `tbname`）

当 `column` 中包含 `tbname` 时，插件使用 `INSERT INTO tbname USING stable TAGS(...) VALUES(...)` 语法自动创建子表。

### 7.2 写入超表（不含 `tbname`）

使用 schemaless 写入（Line Protocol），自动创建子表。需要保证标签值能唯一确定子表名。

### 7.3 写入子表

直接向已存在的子表写入数据。

### 7.4 写入普通表

直接向不包含标签的普通表写入数据。

## 8. 注意事项

- 使用 `jdbc:TAOS-RS://` REST 连接方式
- TDengine 3.x 下 `describe` 返回的列名为小写（`field`/`type`/`length`/`note`），本插件已适配
- 超表名称需通过 `show stables` 获取（TDengine 3.x 的 `show tables` 不包含超表）
- JDBC 驱动推荐使用 `taos-jdbcdriver-3.3.0+` 以兼容 TDengine 3.x

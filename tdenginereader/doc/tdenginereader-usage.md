# tdenginereader 使用文档

## 1. TDengine Docker 环境搭建

```bash
# 启动 TDengine 容器（REST 端口 6041）
docker run -d --name tdengine \
  -p 6041:6041 \
  tdengine/tdengine:3.3.0.3

# 验证连接
docker exec tdengine taos -s "SELECT 1"
```

## 2. 创建测试数据

```bash
docker exec tdengine taos -s "
CREATE DATABASE IF NOT EXISTS testdb;
USE testdb;
CREATE STABLE IF NOT EXISTS meters (
  ts TIMESTAMP,
  current FLOAT,
  voltage INT,
  phase FLOAT
) TAGS (location BINARY(64), groupid INT);

INSERT INTO testdb.meters VALUES
  ('2024-06-16 12:00:00', 10.5, 220, 0.3, 'Beijing', 1),
  ('2024-06-16 12:00:01', 11.2, 221, 0.4, 'Beijing', 1),
  ('2024-06-16 12:00:02', 9.8, 219, 0.2, 'Shanghai', 2),
  ('2024-06-16 12:00:03', 12.1, 222, 0.5, 'Beijing', 1),
  ('2024-06-16 12:00:04', 10.0, 220, 0.3, 'Shanghai', 2);
"
```

## 3. 获取容器 IP

```bash
docker inspect tdengine --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
# 输出示例: 172.17.0.2
```

## 4. 提交 DataX 任务

### 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "tdenginereader",
          "parameter": {
            "username": "root",
            "password": "taosdata",
            "connection": [
              {
                "table": ["testdb.meters"],
                "jdbcUrl": [
                  "jdbc:TAOS-RS://127.0.0.1:6041?timestampFormat=TIMESTAMP"
                ]
              }
            ],
            "column": [
              "ts", "current", "voltage", "phase", "location", "groupid"
            ],
            "where": "1=1"
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
python datax/bin/datax.py job_tdenginereader.json
```

## 5. 参数说明

| 参数 | 必须 | 说明 |
|------|------|------|
| `username` | 是 | TDengine 用户名，默认 `root` |
| `password` | 是 | TDengine 密码 |
| `jdbcUrl` | 是 | JDBC 连接地址，格式 `jdbc:TAOS-RS://host:6041?timestampFormat=TIMESTAMP`。注意：**数据库名不能写在 URL 中**，应使用 `table` 参数中的 `db.table` 格式 |
| `table` | 是 | 待读取的表名列表，格式 `db.table`（数据库限定） |
| `column` | 是 | 读取的列名列表 |
| `where` | 否 | 过滤条件（不能为空字符串），默认 `_c0 > -MAX`。建议始终设置 `"where": "1=1"` |

## 6. 注意事项

- 使用 REST 协议连接 TDengine 时，`timestampFormat=TIMESTAMP` 参数会将时间戳统一转换为毫秒时间戳
- `where` 参数不能留空（空字符串会被当作有效值并覆盖默认缺省值导致 SQL 错误）
- 表名须包含数据库名前缀，格式为 `db.table`
- 支持 TDengine 2.x 和 3.x

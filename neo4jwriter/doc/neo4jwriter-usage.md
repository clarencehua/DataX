# Neo4jWriter 使用文档

> **注意**：`plugin.json` 中插件名称为 `neo4jWriter`（大写 W），配置时必须使用 `"name": "neo4jWriter"`。

## 1. Neo4j Docker 环境搭建

```bash
# 启动 Neo4j 5 容器
docker run -d --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/datax123 \
  neo4j:5-community

# 等待就绪后验证连接
curl -s -X POST http://localhost:7474/db/neo4j/tx/commit \
  -H "Authorization: Basic $(echo -n neo4j:datax123 | base64)" \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"RETURN 1 AS n"}]}'
```

## 2. 提交 DataX 任务

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
              { "type": "long", "value": 1 },
              { "type": "string", "value": "Alice" },
              { "type": "double", "value": 95.5 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "neo4jWriter",
          "parameter": {
            "uri": "neo4j://localhost:7687",
            "username": "neo4j",
            "password": "datax123",
            "database": "neo4j",
            "cypher": "unwind $batch as row create(p:TestNode) set p.id = row.id, p.name = row.name, p.score = row.score",
            "batchDataVariableName": "batch",
            "batchSize": 100,
            "properties": [
              { "name": "id", "type": "LONG" },
              { "name": "name", "type": "STRING" },
              { "name": "score", "type": "DOUBLE" }
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
python datax/bin/datax.py job_neo4j.json
```

## 3. 验证数据

```bash
curl -s -X POST http://localhost:7474/db/neo4j/tx/commit \
  -H "Authorization: Basic $(echo -n neo4j:datax123 | base64)" \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"MATCH (n:TestNode) RETURN n.id, n.name, n.score ORDER BY n.id"}]}'
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `uri` | 是 | - | Neo4j 连接地址，格式 `neo4j://host:port` |
| `username` | 是（与 token 二选一） | - | 用户名 |
| `password` | 是（与 token 二选一） | - | 密码 |
| `bearerToken` | 否 | - | Base64 编码的 bearer token（替代密码认证） |
| `kerberosTicket` | 否 | - | Base64 编码的 kerberos ticket |
| `database` | 否 | `null` | 数据库名称（Neo4j 4.x+ 支持，3.x 不需要） |
| `cypher` | 是 | - | Cypher 查询语句，使用 `$batch` 变量接收行数据 |
| `batchDataVariableName` | 否 | `"batch"` | Cypher 中行数据的变量名 |
| `batchSize` | 否 | `1000` | 每批写入的行数 |
| `properties` | 是 | - | 列属性定义列表 |
| `retryTimes` | 否 | `3` | 写入失败重试次数 |
| `retrySleepMills` | 否 | `3000` | 重试间隔（毫秒） |
| `maxTransactionRetryTimeSeconds` | 否 | `30` | 事务最大重试时间（秒） |
| `maxConnectionTimeoutSeconds` | 否 | `30` | 连接超时时间（秒） |

### properties 定义说明

| 属性 | 必须 | 说明 |
|------|------|------|
| `name` | 是 | Neo4j 属性名，Cypher 中用 `row.{name}` 引用 |
| `type` | 是 | 数据类型：`BOOLEAN` / `STRING` / `LONG` / `INT` / `SHORT` / `DOUBLE` / `FLOAT` / `STRING_ARRAY` / `LOCAL_DATE` / `LOCAL_DATETIME` / `LOCAL_TIME` |
| `dateFormat` | 否 | 日期格式（仅对日期类型有效） |
| `split` | 否 | 字符串切分符（仅对 `STRING_ARRAY` 类型有效，默认 `,`） |

## 5. Cypher 说明

Cypher 中使用 `unwind $batch as row` 遍历批量数据：
- `$batch` 是 `batchDataVariableName` 指定的变量名
- `row.{name}` 引用 `properties` 中定义的列名
- 示例：`unwind $batch as row create(p:Person) set p.name = row.name, p.age = row.age`

## 6. 注意事项

- `plugin.json` 中插件名为 `neo4jWriter`（大写 W），不是 `neo4jwriter`
- 如果使用 Neo4j 3.x，不需要设置 `database` 参数
- 支持 auth: basic（用户名+密码）、bearer token、kerberos ticket 三种认证方式（三选一）
- 支持 5 种类型：节点属性、节点属性写入、关系属性写入
- 写入时自动按 `batchSize` 批量提交

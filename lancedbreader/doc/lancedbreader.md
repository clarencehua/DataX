# DataX lancedbreader

## 1 快速介绍

lancedbreader 插件实现了从 LanceDB 表读取数据的功能。面向 ETL 开发工程师，使用 lancedbreader 从 LanceDB 导入数据到数仓。

## 2 实现原理

lancedbreader 通过 DataX 框架从 LanceDB 读取数据。使用 LanceDB Java REST SDK 查询表数据，查询结果以 Apache Arrow IPC 格式返回，插件将 Arrow 列式数据解析为 DataX Record。

## 3 功能说明

### 3.1 配置样例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "lancedbreader",
          "parameter": {
            "apiKey": "your_lancedb_api_key",
            "endpoint": "https://your-endpoint.com:443",
            "database": "your_database",
            "table": "my_table",
            "column": [
              {
                "name": "id",
                "type": "Int64"
              },
              {
                "name": "name",
                "type": "String"
              },
              {
                "name": "score",
                "type": "Double"
              },
              {
                "name": "flag",
                "type": "Bool"
              },
              {
                "name": "embedding",
                "type": "FloatVector",
                "dimension": 4
              }
            ],
            "filter": "id > 0",
            "batchSize": 10000
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": {
            "print": true
          }
        }
      }
    ],
    "setting": {
      "errorLimit": {
        "record": "0"
      },
      "speed": {
        "channel": 1
      }
    }
  }
}
```

### 3.2 参数说明

| 参数名 | 必选 | 默认值 | 描述 |
|--------|------|--------|------|
| apiKey | 是 | 无 | LanceDB API 密钥 |
| endpoint | 否 | 无 | LanceDB Enterprise 自定义端点 |
| database | 是 | 无 | 数据库名称 |
| table | 是 | 无 | 目标表名称 |
| namespace | 否 | 空 | 命名空间路径 |
| column | 是 | 无 | 字段定义列表 |
| filter | 否 | 空 | SQL 过滤条件 |
| batchSize | 否 | 10000 | 单次查询返回的最大行数 |
| region | 否 | us-east-1 | AWS 区域 |

### 3.3 column 字段属性

| 属性名 | 必选 | 描述 |
|--------|------|------|
| name | 是 | 字段名 |
| type | 是 | 数据类型 |
| dimension | 否 | 向量维度，FloatVector 类型必填 |

### 3.4 支持的数据类型

Int8, Int16, Int32, Int64, Float, Double, String, Bool, Binary, FloatVector

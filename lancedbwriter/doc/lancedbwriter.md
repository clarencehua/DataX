# DataX lancedbwriter

## 1 快速介绍

lancedbwriter 插件实现了写入数据到 LanceDB 表的功能。面向 ETL 开发工程师，使用 lancedbwriter 从数仓导入数据到 LanceDB。

## 2 实现原理

lancedbwriter 通过 DataX 框架获取 Reader 生成的协议数据，通过 `insert/mergeInsert` 方式写入数据到 LanceDB，并通过 batchSize 累积的方式进行数据提交。

LanceDB Java SDK 使用 Apache Arrow IPC 格式进行数据交换，插件内部将 DataX Record 转换为 Arrow 列式数据后再提交。

## 3 功能说明

### 3.1 配置样例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              {
                "value": 1,
                "type": "long"
              },
              {
                "value": "[1.1, 2.2, 3.3, 4.4]",
                "type": "string"
              },
              {
                "value": "test_name",
                "type": "string"
              },
              {
                "value": 3.14,
                "type": "double"
              },
              {
                "value": true,
                "type": "bool"
              }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "lancedbwriter",
          "parameter": {
            "apiKey": "your_lancedb_api_key",
            "endpoint": "https://your-endpoint.com:443",
            "database": "your_database",
            "namespace": "",
            "table": "my_table",
            "schemaCreateMode": "createIfNotExist",
            "writeMode": "insert",
            "batchSize": 1024,
            "column": [
              {
                "name": "id",
                "type": "Int64",
                "primaryKey": "true"
              },
              {
                "name": "embedding",
                "type": "FloatVector",
                "dimension": 4
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
              }
            ]
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
| batchSize | 否 | 100 | 批量提交记录数 |
| schemaCreateMode | 否 | createIfNotExist | 表创建模式: createIfNotExist, ignore, recreate |
| writeMode | 否 | insert | 写入模式: insert, upsert |
| region | 否 | us-east-1 | AWS 区域 |
| column | 是 | 无 | 字段定义列表 |

### 3.3 column 字段属性

| 属性名 | 必选 | 描述 |
|--------|------|------|
| name | 是 | 字段名 |
| type | 是 | 数据类型 |
| primaryKey | 否 | 是否为主键，upsert 模式需要 |
| dimension | 否 | 向量维度，FloatVector 类型必填 |
| maxLength | 否 | 字符串最大长度 |

### 3.4 支持的数据类型

Int8, Int16, Int32, Int64, Float, Double, String, Bool, Binary, FloatVector

# lancedbreader 使用文档

## 1. 功能说明
从 LanceDB 表读取数据。

## 2. 参数说明

| 参数名 | 必选 | 默认值 | 描述 |
|--------|------|--------|------|
| apiKey | 是 | 无 | LanceDB API 密钥 |
| endpoint | 否 | 无 | LanceDB Enterprise 自定义端点 |
| database | 是 | 无 | 数据库名称 |
| table | 是 | 无 | 目标表名称 |
| namespace | 否 | 空 | 命名空间路径 |
| column | 是 | 无 | 字段定义列表 |
| column[].name | 是 | 无 | 字段名 |
| column[].type | 是 | 无 | 数据类型 |
| column[].dimension | 否 | 无 | FloatVector 维度 |
| filter | 否 | 空 | SQL 过滤条件 |
| batchSize | 否 | 10000 | 单次查询返回的最大行数 |

## 3. 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "lancedbreader",
          "parameter": {
            "apiKey": "your_api_key",
            "database": "your_database",
            "table": "datax_test_table",
            "column": [
              { "name": "id", "type": "Int64" },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" },
              { "name": "embedding", "type": "FloatVector", "dimension": 4 }
            ],
            "filter": "id >= 0"
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
      "speed": {
        "channel": 1
      }
    }
  }
}
```

## 4. 运行

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_lancedbreader.json
```

## 5. 注意事项

- column 中字段顺序对应输出列
- FloatVector 类型返回 JSON 数组字符串格式
- filter 使用 LanceDB SQL 语法

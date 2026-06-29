# lancedbwriter 使用文档

## 1. 功能说明
将数据写入 LanceDB 表。

## 2. 参数说明

| 参数名 | 必选 | 默认值 | 描述 |
|--------|------|--------|------|
| apiKey | 是 | 无 | LanceDB API 密钥 |
| endpoint | 否 | 无 | LanceDB Enterprise 自定义端点 |
| database | 是 | 无 | 数据库名称 |
| table | 是 | 无 | 目标表名称 |
| namespace | 否 | 空 | 命名空间路径（可多级） |
| batchSize | 否 | 100 | 批量提交记录数 |
| schemaCreateMode | 否 | createIfNotExist | 表创建模式 |
| writeMode | 否 | insert | 写入模式 |
| region | 否 | us-east-1 | AWS 区域 |
| column | 是 | 无 | 字段定义列表 |

### writeMode 说明
- `insert`: 追加写入（默认）
- `upsert`: 按主键合并写入（需要 column 中设置 primaryKey）

### schemaCreateMode 说明
- `createIfNotExist`: 表不存在则创建（默认）
- `ignore`: 表不存在则报错
- `recreate`: 删除表并重建

## 3. 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "value": 1, "type": "long" },
              { "value": "[1.0, 2.0, 3.0, 4.0]", "type": "string" },
              { "value": "Hello LanceDB", "type": "string" },
              { "value": 3.14, "type": "double" },
              { "value": true, "type": "bool" }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "lancedbwriter",
          "parameter": {
            "apiKey": "your_api_key",
            "database": "your_database",
            "table": "datax_test_table",
            "schemaCreateMode": "recreate",
            "writeMode": "insert",
            "batchSize": 100,
            "column": [
              { "name": "id", "type": "Int64", "primaryKey": true },
              { "name": "embedding", "type": "FloatVector", "dimension": 4 },
              { "name": "name", "type": "String" },
              { "name": "score", "type": "Double" },
              { "name": "flag", "type": "Bool" }
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

## 4. 运行

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_lancedbwriter.json
```

## 5. 注意事项

- `column` 中字段顺序必须与 reader 的输出列一一对应
- 向量数据以 JSON 数组字符串形式传入，如 `[1.0, 2.0, 3.0, 4.0]`
- FloatVector 类型需要指定 dimension
- upsert 模式需要指定 primaryKey

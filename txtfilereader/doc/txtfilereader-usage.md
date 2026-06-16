# TxtFileReader 使用文档

## 1. 准备测试文件

```bash
cat > /tmp/test_data.csv << EOF
1,apple,3.5,true
2,banana,4.2,false
3,cherry,5.0,true
EOF
```

## 2. 提交 DataX 任务

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "txtfilereader",
          "parameter": {
            "path": ["/tmp/test_data.csv"],
            "encoding": "UTF-8",
            "column": [
              { "index": 0, "type": "long" },
              { "index": 1, "type": "string" },
              { "index": 2, "type": "double" },
              { "index": 3, "type": "boolean" }
            ],
            "fieldDelimiter": ","
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

```bash
python datax/bin/datax.py job_txtfile.json
```

## 3. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `path` | 是 | - | 文件路径数组，支持 `*`/`?` 通配符，支持目录递归 |
| `column` | 是 | `["*"]` | 列定义：`index`（位置）或 `value`（常量），支持 `long`/`string`/`double`/`boolean`/`date` |
| `fieldDelimiter` | 是 | `","` | 列分隔符（单字符） |
| `encoding` | 否 | `"UTF-8"` | 文件编码 |
| `compress` | 否 | - | 压缩格式：`gzip`/`bzip2`/`zip`/`lzo`/`lzo_deflate`/`hadoop-snappy`/`framing-snappy` |
| `skipHeader` | 否 | `false` | 是否跳过首行 |
| `nullFormat` | 否 | `\N` | null 值标识 |
| `maxTraversalLevel` | 否 | `100` | 目录递归最大深度 |
| `skipEmptyRecords` | 否 | `true` | 是否跳过空行 |
| `csvReaderConfig` | 否 | - | CSV 解析器配置（JSON） |

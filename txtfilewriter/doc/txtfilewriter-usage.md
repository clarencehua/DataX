# TxtFileWriter 使用文档

## 1. 提交 DataX 任务

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "type": "long", "value": 42 },
              { "type": "string", "value": "hello" },
              { "type": "double", "value": 9.99 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "txtfilewriter",
          "parameter": {
            "path": "/tmp/output",
            "fileName": "result",
            "writeMode": "truncate",
            "fieldDelimiter": ",",
            "encoding": "UTF-8"
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
python datax/bin/datax.py job_txtfilewriter.json
```

## 2. 验证数据

```bash
ls -la /tmp/output/
cat /tmp/output/result__*
```

## 3. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `path` | 是 | - | 输出目录路径（本地） |
| `fileName` | 是 | - | 输出文件前缀（DataX 自动追加 UUID 后缀） |
| `writeMode` | 是 | - | `"truncate"`（覆盖）/ `"append"`（追加）/ `"nonConflict"`（冲突报错） |
| `fieldDelimiter` | 否 | `","` | 列分隔符 |
| `encoding` | 否 | `"UTF-8"` | 文件编码 |
| `dateFormat` | 否 | - | 日期格式 |
| `nullFormat` | 否 | `\N` | null 值输出格式 |
| `fileFormat` | 否 | `"text"` | 文件格式：`text` / `csv` / `sql` |
| `suffix` | 否 | `""` | 文件后缀 |
| `header` | 否 | - | 写入首行的列名数组 |
| `compress` | 否 | - | 压缩格式：`gzip` / `bzip2` |
| `maxFileSize` | 否 | - | 单文件大小上限（字节） |

## 4. 写入模式说明

| writeMode | 行为 |
|-----------|------|
| `truncate` | 写入前删除 `path/` 下匹配 `fileName` 的文件 |
| `append` | 直接追加写入 |
| `nonConflict` | 文件已存在时报错 |

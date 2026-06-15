# HdfsWriter 使用文档

## 1. HDFS Docker 环境搭建

参见 hdfsreader-usage.md 中的 HDFS Docker 搭建步骤。

## 2. 创建输出目录

```bash
docker exec --user hadoop hadoop /opt/hadoop/bin/hdfs dfs -mkdir -p /data/output
```

## 3. 提交 DataX 任务

### TEXT 文件写入

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              {"value": 100, "type": "long"},
              {"value": "datax_test", "type": "string"},
              {"value": 88.5, "type": "double"}
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "hdfswriter",
          "parameter": {
            "defaultFS": "hdfs://<hdfs_ip>:9000",
            "fileType": "text",
            "path": "/data/output",
            "fileName": "result",
            "column": [
              {"name": "id", "type": "bigint"},
              {"name": "name", "type": "string"},
              {"name": "score", "type": "double"}
            ],
            "writeMode": "append",
            "fieldDelimiter": "\t",
            "encoding": "UTF-8"
          }
        }
      }
    ],
    "setting": { "speed": { "channel": 1 } }
  }
}
```

```bash
python $DATAX_HOME/bin/datax.py job.json
```

### ORC 文件写入

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              {"value": 100, "type": "long"},
              {"value": "datax_test", "type": "string"},
              {"value": 88.5, "type": "double"}
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "hdfswriter",
          "parameter": {
            "defaultFS": "hdfs://<hdfs_ip>:9000",
            "fileType": "orc",
            "path": "/data/output",
            "fileName": "result",
            "column": [
              {"name": "id", "type": "bigint"},
              {"name": "name", "type": "string"},
              {"name": "score", "type": "double"}
            ],
            "writeMode": "append",
            "fieldDelimiter": "\t",
            "encoding": "UTF-8"
          }
        }
      }
    ],
    "setting": { "speed": { "channel": 1 } }
  }
}
```

## 4. 参数说明

| 参数名 | 是否必填 | 说明 |
|--------|----------|------|
| `defaultFS` | 是 | HDFS NameNode 地址，格式 `hdfs://host:port` |
| `fileType` | 是 | 文件类型：`text`, `orc`, `parquet` |
| `path` | 是 | HDFS 目标路径（绝对路径，需提前创建） |
| `fileName` | 是 | 文件名前缀 |
| `column` | 是 | 列定义，包含 `name` 和 `type` |
| `writeMode` | 是 | 写入模式：`append`, `nonConflict`, `truncate` |
| `fieldDelimiter` | 是 | 字段分隔符（单字符，TEXT 必填；ORC 也必填） |
| `encoding` | 否 | 编码，默认 `UTF-8` |
| `compress` | 否 | 压缩：TEXT 支持 `GZIP`, `BZIP2`；ORC 支持 `NONE`, `SNAPPY` |
| `nullFormat` | 否 | null 值表示，默认 `\N` |
| `hadoopConfig` | 否 | 高级 Hadoop 配置 |
| `haveKerberos` | 否 | 是否使用 Kerberos，默认 false |
| `hdfsUsername` | 否 | HDFS 客户端用户名，默认 `admin` |

## 5. 类型映射

| HDFS 列类型 | DataX 内部类型 |
|-------------|---------------|
| TINYINT, SMALLINT, INT, BIGINT | Long |
| FLOAT, DOUBLE | Double |
| STRING, VARCHAR, CHAR | String |
| BOOLEAN | Boolean |
| DATE, TIMESTAMP | Date |
| BINARY | Bytes |

## 6. 验证结果

```bash
# 查看写入的文件
docker exec --user hadoop hadoop bash -c '/opt/hadoop/bin/hdfs dfs -ls /data/output/'

# 读取 TEXT 内容
docker exec --user hadoop hadoop bash -c '/opt/hadoop/bin/hdfs dfs -cat /data/output/*'

# 读取 ORC 内容（需使用 orc-tools 或 hdfsreader）
```

TEXT 输出内容：
```
100	datax_test	88.5
```

任务日志：
```
读出记录总数: 1
读写失败总数: 0
```

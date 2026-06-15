# elasticsearchwriter Usage Guide

## 1. Start Elasticsearch Docker Container

```bash
docker run -d --name elasticsearch \
  -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  docker.elastic.co/elasticsearch/elasticsearch:7.17.28
```

Verify:
```bash
curl -s "http://127.0.0.1:9200"
# Expected: cluster info JSON with version.number = "7.17.28"
```

## 2. Job Configuration (StreamReader → ElasticsearchWriter)

Create `/tmp/job_elasticsearchwriter.json`:

```json
{
    "job": {
        "setting": {
            "speed": {
                "channel": 1
            }
        },
        "content": [
            {
                "reader": {
                    "name": "streamreader",
                    "parameter": {
                        "sliceRecordCount": 1,
                        "column": [
                            {
                                "type": "long",
                                "value": 100
                            },
                            {
                                "type": "string",
                                "value": "datax_test"
                            },
                            {
                                "type": "double",
                                "value": 88.5
                            },
                            {
                                "type": "bool",
                                "value": true
                            }
                        ]
                    }
                },
                "writer": {
                    "name": "elasticsearchwriter",
                    "parameter": {
                        "endpoint": "http://127.0.0.1:9200",
                        "index": "test_idx",
                        "cleanup": true,
                        "batchSize": 1000,
                        "settings": {
                            "index": {
                                "number_of_shards": 1,
                                "number_of_replicas": 0
                            }
                        },
                        "column": [
                            {
                                "name": "id",
                                "type": "id"
                            },
                            {
                                "name": "name",
                                "type": "keyword"
                            },
                            {
                                "name": "score",
                                "type": "double"
                            },
                            {
                                "name": "active",
                                "type": "boolean"
                            }
                        ]
                    }
                }
            }
        ]
    }
}
```

### Parameter Description

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `endpoint` | Yes | - | ES HTTP URL, e.g. `http://127.0.0.1:9200` |
| `accessId` / `username` | No | `""` | HTTP auth username |
| `accessKey` / `password` | No | `""` | HTTP auth password |
| `index` | Yes | - | ES index name |
| `indexType` / `type` | No | index name | ES type name (deprecated in 7.x, ignored by ES 8+) |
| `cleanup` / `truncate` | No | `false` | Delete and recreate index before writing |
| `batchSize` | No | `1024` | Bulk batch size (records) |
| `trySize` | No | `30` | Number of retries on failure |
| `tryInterval` | No | `60000` | Retry interval (ms) |
| `timeout` | No | `600000` | Client timeout (ms) |
| `discovery` | No | `false` | Enable node discovery (polling server list) |
| `compression` / `compress` | No | `true` | Enable HTTP compression |
| `multiThread` | No | `true` | Enable multi-threaded HTTP requests |
| `ignoreWriteError` | No | `false` | Ignore write errors (continue without retry) |
| `ignoreParseError` | No | `true` | Ignore data parse errors (continue writing) |
| `alias` | No | `""` | Alias name to add after data import |
| `aliasMode` | No | `append` | `append` (add alias) or `exclusive` (replace all aliases) |
| `settings` | No | `{}` | Index creation settings (same as ES `index.*`) |
| `splitter` | No | `-,-` | Separator for array fields |
| `dynamic` | No | `false` | Use ES auto-mapping instead of DataX-defined mappings |
| `dstDynamic` | No | `null` | Custom dynamic mapping template |
| `actionType` | No | `index` | `index` (put/overwrite), `create` (fail-if-exists), `update` (upsert), `delete` |
| `enableWriteNull` | No | `true` | Whether to write null-valued fields on UPDATE |
| `esVersion` | No | auto-detect | Explicit ES major version (e.g. `7`) |
| `masterTimeout` | No | `5m` | Master node timeout for index operations |
| `primaryKeyInfo` | No | `null` | JSON: `{"type":"pk", "fieldDelimiter":"-", "column":["col1","col2"]}` |
| `esPartitionColumn` | No | `null` | JSON array of partition column objects |
| `urlParams` | No | `{}` | Additional URL query params for bulk requests |
| `fieldDelimiter` | No | `""` | Field delimiter for combined fields |

### Column Definition

Each column in the `column` array supports:

| Property | Required | Description |
|----------|----------|-------------|
| `name` | Yes | Field name in ES document |
| `type` | Yes | ES field type (see supported types below) |
| `array` | No | `true` if the field is an array |
| `splitter` | No | Separator to split string into array (default: splitter from root config) |
| `format` | No | Date format pattern (e.g. `yyyy-MM-dd HH:mm:ss`) |
| `timezone` | No | Timezone for date fields |
| `analyzer` | No | Analyzer for `text` fields (e.g. `ik_max_word`) |
| `jsonArray` | No | `true` if the value is a JSON array string |

### Supported Field Types

`id`, `parent`, `routing`, `version`, `string`, `text`, `keyword`, `long`, `integer`, `short`, `byte`, `double`, `float`, `date`, `boolean`, `binary`, `integer_range`, `float_range`, `long_range`, `double_range`, `date_range`, `geo_point`, `geo_shape`, `ip`, `ip_range`, `completion`, `token_count`, `object`, `nested`

The `id` type is special: the field value is used as the ES document `_id`.

## 3. Run DataX

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_elasticsearchwriter.json
```

Expected output:

```
读出记录总数                    :                   1
读写失败总数                    :                   0
```

## 4. Verify Written Data

```bash
curl -s "http://127.0.0.1:9200/test_idx/_search?pretty"
```

Expected output:

```json
{
  "hits": {
    "total": { "value": 1, "relation": "eq" },
    "hits": [
      {
        "_index": "test_idx",
        "_type": "_doc",
        "_id": "100",
        "_source": {
          "score": 88.5,
          "name": "datax_test",
          "active": true
        }
      }
    ]
  }
}
```

## 5. Test Multiple Scenarios

### 5.1 Bulk Insert (Multiple Records)

Change `sliceRecordCount` to generate multiple records (they will have the same data; use different readers for diverse data):

```json
"sliceRecordCount": 3
```

### 5.2 Write without Cleanup

```json
"cleanup": false
```

Useful for appending to an existing index.

### 5.3 Update Existing Documents

Set `actionType` to `update` and include an `id` column:

```json
"actionType": "update"
```

### 5.4 Create (Fail on Duplicate)

```json
"actionType": "create"
```

If a document with the same `_id` already exists, the write will fail for that record.

### 5.5 Using ES Auto-Mapping

```json
"dynamic": true
```

Skip DataX-defined mappings and let ES infer field types automatically.

### 5.6 Date Fields with Format

```json
{
    "name": "create_time",
    "type": "date",
    "format": "yyyy-MM-dd HH:mm:ss"
}
```

## 6. Authentication Example

If ES has security enabled:

```json
"endpoint": "http://127.0.0.1:9200",
"username": "elastic",
"password": "changeme"
```

## 7. Cleanup

```bash
docker stop elasticsearch && docker rm elasticsearch
```

## 8. Notes

- Uses Jest HTTP client 6.3.1 — compatible with ES 6.x and 7.x (code auto-detects cluster version >= 7 for mapping API format).
- The `cleanup: true` option **deletes the index** before recreating it. Use with caution.
- When `type` is not specified, it defaults to the index name. In ES 7.x, the stored `_type` is always `_doc`.
- The `id` column type sets the ES document `_id`. Without it, ES auto-generates `_id`.
- Column `name` `pk` is automatically treated as type `id`.
- The `plugin_job_template.json` does not exist for this plugin — use the doc examples as reference.
- ES 8.x has not been tested with this plugin. The Jest 6.3.1 client may have compatibility issues.

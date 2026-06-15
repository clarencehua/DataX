# mongodbwriter Usage Guide

## 1. Start MongoDB Docker Container

```bash
docker run -d --name mongodb -p 27017:27017 mongo:4.4
```

Verify:
```bash
docker logs mongodb 2>&1 | grep "Waiting for connections"
```

## 2. Test Data for Writer

Create a target collection for writes:

```bash
docker exec -i mongodb mongo --quiet <<'EOF'
use test_db;
db.writer_tbl.drop();
EOF
```

## 3. Job Configuration

Create `/tmp/job_mongodbwriter.json`:

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
                    "name": "mongodbwriter",
                    "parameter": {
                        "address": ["127.0.0.1:27017"],
                        "dbName": "test_db",
                        "collectionName": "writer_tbl",
                        "column": [
                            {
                                "name": "id",
                                "type": "int"
                            },
                            {
                                "name": "name",
                                "type": "string"
                            },
                            {
                                "name": "score",
                                "type": "double"
                            },
                            {
                                "name": "active",
                                "type": "bool"
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

| Parameter | Required | Description |
|-----------|----------|-------------|
| `address` | Yes | JSON array of `"host:port"` strings |
| `userName` | No | MongoDB username |
| `userPassword` | No | MongoDB password |
| `dbName` | Yes | MongoDB database name |
| `collectionName` | Yes | MongoDB collection name |
| `column` | Yes | Array of column definitions |
| `column[].name` | Yes | Column name (field name in MongoDB document) |
| `column[].type` | Yes | Data type: `string`, `int`, `long`, `double`, `bool`, `date`, `bytes`, `array`, `objectid` |
| `column[].splitter` | No | Separator for converting strings to arrays |
| `column[].itemtype` | No | Element type within arrays (for typed arrays) |
| `writeMode` | No | Upsert configuration object |
| `writeMode.isReplace` | No | `"true"` to replace on duplicate key (default: `"false"`) |
| `writeMode.replaceKey` | No | Business key field for matching documents |
| `preSql` | No | Pre-processing SQL. Use `{"type":"drop"}` to drop collection, `{"type":"remove","json":"..."}` to remove matching docs |

### Supported Data Types

| DataX Type | MongoDB Type |
|---|---|
| Long | int, Long |
| Double | double |
| String | string |
| Date | date |
| Boolean | boolean |
| Bytes | bytes |
| Array | array (uses `splitter` to parse string into array) |
| ObjectId | objectid (for `_id` field) |

## 4. Run DataX

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_mongodbwriter.json
```

Expected output:

```
任务启动时刻                    : 2026-06-15 15:45:02
任务结束时刻                    : 2026-06-15 15:45:12
任务总计耗时                    :                 10s
读出记录总数                    :                   1
读写失败总数                    :                   0
```

## 5. Verify Written Data

```bash
docker exec -i mongodb mongo --quiet <<'EOF'
use test_db;
db.writer_tbl.find().pretty();
EOF
```

Expected output:

```json
{
    "_id" : ObjectId("..."),
    "id" : 100,
    "name" : "datax_test",
    "score" : 88.5,
    "active" : true
}
```

## 6. Upsert Example

To update existing documents by a business key, use `writeMode`:

```json
"writer": {
    "name": "mongodbwriter",
    "parameter": {
        "address": ["127.0.0.1:27017"],
        "dbName": "test_db",
        "collectionName": "writer_tbl",
        "column": [
            { "name": "unique_id", "type": "string" },
            { "name": "value", "type": "int" }
        ],
        "writeMode": {
            "isReplace": "true",
            "replaceKey": "unique_id"
        }
    }
}
```

## 7. Array Type Example

To write a string separated by `splitter` as a MongoDB array:

```json
"column": [
    { "name": "tags", "type": "array", "splitter": ",", "itemtype": "string" }
]
```

Input record with value `"a,b,c"` will be stored in MongoDB as `["a", "b", "c"]`.

## 8. Pre-processing Example

To drop a collection before writing, add `preSql` at the writer parameter level:

```json
"writer": {
    "name": "mongodbwriter",
    "parameter": {
        "address": ["127.0.0.1:27017"],
        "dbName": "test_db",
        "collectionName": "writer_tbl",
        "preSql": {"type": "drop"},
        "column": [...]
    }
}
```

## 9. Cleanup

```bash
docker stop mongodb && docker rm mongodb
```

## 10. Notes

- The Java driver version is 3.2.2 (compatible with MongoDB 3.x–4.x). For MongoDB 5+, you may need to update the driver version in `pom.xml`.
- Without `writeMode` (or `writeMode.isReplace` not set to `"true"`), the writer uses `insertMany`. With `isReplace="true"`, it uses `bulkWrite` with `ReplaceOneModel` (upsert).
- The `_id` field is auto-generated by MongoDB. If you need to insert a custom `_id`, add `{ "name": "_id", "type": "objectid" }` or `{ "name": "_id", "type": "string" }` to the column list.
- `preSql` is not technically SQL — it uses a custom JSON format specific to the MongoDB writer.
- The `plugin_job_template.json` uses the old `upsertInfo` key name (with `isUpsert`/`upsertKey`), but the actual source code uses `writeMode` (with `isReplace`/`replaceKey`). Use `writeMode` in your configurations.

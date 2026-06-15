# mongodbreader Usage Guide

## 1. Start MongoDB Docker Container

```bash
docker run -d --name mongodb -p 27017:27017 mongo:4.4
```

Verify:
```bash
docker logs mongodb 2>&1 | grep "Waiting for connections"
```

## 2. Create Test Data

```bash
docker exec -i mongodb mongo --quiet <<'EOF'
use test_db;
db.test_collection.insertMany([
  { "name": "Alice", "age": 25, "score": 88.5, "active": true },
  { "name": "Bob", "age": 30, "score": 92.3, "active": true },
  { "name": "Charlie", "age": 35, "score": 75.0, "active": false },
  { "name": "Diana", "age": 28, "score": 95.8, "active": true },
  { "name": "Eve", "age": 22, "score": 68.5, "active": false }
]);
db.test_collection.find().pretty();
EOF
```

## 3. Job Configuration

Create `/tmp/job_mongodbreader.json`:

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
                    "name": "mongodbreader",
                    "parameter": {
                        "address": ["127.0.0.1:27017"],
                        "dbName": "test_db",
                        "collectionName": "test_collection",
                        "column": [
                            {
                                "name": "_id",
                                "type": "string"
                            },
                            {
                                "name": "name",
                                "type": "string"
                            },
                            {
                                "name": "age",
                                "type": "int"
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
                },
                "writer": {
                    "name": "streamwriter",
                    "parameter": {
                        "print": true
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
| `authDb` | No | Authentication database (defaults to `dbName`) |
| `dbName` | Yes | MongoDB database name |
| `collectionName` | Yes | MongoDB collection name |
| `column` | Yes | Array of column definitions |
| `column[].name` | Yes | Column name (field name in MongoDB document) |
| `column[].type` | No | Data type: `string`, `int`, `long`, `double`, `bool`, `date`, `bytes`, `array`, `document`, `document.array` |
| `column[].splitter` | No | Separator to flatten array columns into strings |
| `query` | No | Extra MongoDB query filter (JSON string) |
| `batchSize` | No | Batch size for fetching records |

### Supported Data Types

| DataX Type | MongoDB Type |
|---|---|
| Long | int, Long |
| Double | double |
| String | string, array |
| Date | date |
| Boolean | boolean |
| Bytes | bytes |

## 4. Run DataX

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_mongodbreader.json
```

Expected output (5 records):

```
6a2fad5e1655856c2040b7ba	Alice	25.0	88.5	true
6a2fad5e1655856c2040b7bb	Bob	30.0	92.3	true
6a2fad5e1655856c2040b7bc	Charlie	35.0	75.0	false
6a2fad5e1655856c2040b7bd	Diana	28.0	95.8	true
6a2fad5e1655856c2040b7be	Eve	22.0	68.5	false
```

## 5. Authentication Example

```bash
docker run -d --name mongodb-auth \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=admin123 \
  -p 27018:27017 mongo:4.4
```

Job config with auth:

```json
{
    "reader": {
        "name": "mongodbreader",
        "parameter": {
            "address": ["127.0.0.1:27018"],
            "userName": "admin",
            "userPassword": "admin123",
            "authDb": "admin",
            "dbName": "test_db",
            "collectionName": "test_collection",
            "column": [
                { "name": "name", "type": "string" }
            ]
        }
    },
    "writer": {
        "name": "streamwriter",
        "parameter": { "print": true }
    }
}
```

## 6. Cleanup

```bash
docker stop mongodb && docker rm mongodb
```

## 7. Notes

- The Java driver version is 3.2.2 (compatible with MongoDB 3.x–4.x). For MongoDB 5+, you may need to update the driver version in `pom.xml`.
- The `splitter` field only works for columns declared with `"type": "array"` or `"type": "document.array"`.
- The `_id` field returned by MongoDB is an `ObjectId`. If you need it as a string, declare `"type": "string"`.
- The reader splits the collection into shards using `splitVector` command (or `skip/limit` fallback) for parallel reading.

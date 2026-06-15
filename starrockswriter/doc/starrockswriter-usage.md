# starrockswriter Usage Guide

## 1. Start StarRocks Docker Cluster

Same as [starrocksreader](starrocksreader-usage.md), ensure FE (query port 9030, http port 8030) and BE are running.

## 2. Create Target Table

```bash
mysql -h 127.0.0.1 -P 9030 -u root -p'datax_test' <<'EOF'
CREATE DATABASE IF NOT EXISTS test_db;
USE test_db;
CREATE TABLE IF NOT EXISTS writer_tbl (
    id INT,
    name VARCHAR(50),
    score DOUBLE,
    active BOOLEAN
) DISTRIBUTED BY HASH(id) BUCKETS 1
  PROPERTIES ('replication_num' = '1');
EOF
```

## 3. Job Configuration

Create `/tmp/job_starrockswriter.json`:

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
                    "name": "starrockswriter",
                    "parameter": {
                        "username": "root",
                        "password": "datax_test",
                        "database": "test_db",
                        "table": "writer_tbl",
                        "column": ["id", "name", "score", "active"],
                        "loadUrl": ["127.0.0.1:8030"],
                        "jdbcUrl": "jdbc:mysql://127.0.0.1:9030/test_db"
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
| `username` | Yes | - | StarRocks user |
| `password` | Yes | - | StarRocks password |
| `database` | Yes | - | Target database name |
| `selectedDatabase` | No | - | Alternative to `database` (via `connection[].selectedDatabase`) |
| `table` | Yes | - | Target table name |
| `column` | Yes | - | List of column names to write (e.g. `["id","name"]`) |
| `loadUrl` | Yes | - | List of FE HTTP addresses, format `host:port` (default FE HTTP port: 8030) |
| `jdbcUrl` | No | - | JDBC URL for preSql/postSql execution: `jdbc:mysql://host:9030/db` |
| `connection` | No | - | Alternative format: `[{"jdbcUrl":"...", "table":["..."], "selectedDatabase":"..."}]` |
| `preSql` | No | `[]` | SQL statements to run before write |
| `postSql` | No | `[]` | SQL statements to run after write |
| `maxBatchRows` | No | `500000` | Rows per Stream Load batch |
| `maxBatchSize` | No | `104857600` (100MB) | Bytes per Stream Load batch |
| `flushInterval` | No | `300000` (5min) | Max interval between flushes (ms) |
| `loadProps` | No | `{}` | Additional Stream Load parameters (see below) |
| `labelPrefix` | No | (UUID) | Prefix for Stream Load label |

### Stream Load Formats

**CSV (default):** Column separator `\t`, row delimiter `\n`:

```json
"loadProps": {
    "column_separator": "\\x01",
    "row_delimiter": "\\x02"
}
```

**JSON:**

```json
"loadProps": {
    "format": "json",
    "strip_outer_array": true
}
```

### Type Mapping

All DataX types are converted to strings for Stream Load:

| DataX Type | StarRocks Conversion |
|------------|---------------------|
| NULL | `\\N` (null) |
| BOOL | `0` or `1` (asLong) |
| BYTES | Decoded as big-endian integer |
| LONG, INT, DOUBLE, STRING, DATE | `col.asString()` |

## 4. Run DataX

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_starrockswriter.json
```

Expected output:

```
Executing stream load to: 'http://127.0.0.1:8030/api/test_db/writer_tbl/_stream_load', size: '22'
...
读出记录总数                    :                   1
读写失败总数                    :                   0
```

## 5. Verify Written Data

```bash
mysql -h 127.0.0.1 -P 9030 -u root -p'datax_test' -e "SELECT * FROM test_db.writer_tbl"
```

Expected:

```
+------+-----------+-------+--------+
| id   | name      | score | active |
+------+-----------+-------+--------+
|  100 | datax_test|  88.5 |      1 |
+------+-----------+-------+--------+
```

## 6. Writer → Reader Round-Trip

```json
{
    "reader": {
        "name": "starrocksreader",
        "parameter": {
            "username": "root",
            "password": "datax_test",
            "connection": [{
                "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"],
                "table": ["writer_tbl"]
            }],
            "column": ["id", "name", "score", "active"]
        }
    },
    "writer": {
        "name": "streamwriter",
        "parameter": { "print": true }
    }
}
```

## 7. Cleanup

```bash
docker stop starrocks-fe starrocks-be && docker rm starrocks-fe starrocks-be
```

## 8. Notes

- Uses **Stream Load** HTTP API (`POST /api/{db}/{table}/_stream_load`) to FE HTTP port (default 8030).
- `loadUrl` supports multiple FE addresses for high availability (round-robin selection).
- Data is buffered in memory and flushed asynchronously when batch size/rows/interval is reached.
- If Stream Load fails, the writer retries up to 1 time with a new label.
- The `jdbcUrl` is **optional** and only needed if using `preSql`/`postSql`.
- Shaded HTTP/Commons dependencies to avoid classpath conflicts with other DataX plugins.
- BOOLEAN values are serialized as `0`/`1` (matching StarRocks BOOLEAN storage).

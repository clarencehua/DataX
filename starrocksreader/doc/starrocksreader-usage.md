# starrocksreader Usage Guide

## 1. Start StarRocks Docker Cluster

Create a Docker network and start FE + BE:

```bash
# Start FE
docker run -d --name starrocks-fe \
  -p 9030:9030 -p 8030:8030 -p 9020:9020 \
  --privileged=true \
  starrocks/fe-ubuntu:3.3-latest \
  /bin/bash -c "bash /opt/starrocks/fe/bin/start_fe.sh --host_type IP --daemon && sleep 3 && tail -f /opt/starrocks/fe/log/fe.out"

# Wait for FE
FE_IP=$(docker inspect starrocks-fe --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "FE ready at $FE_IP"

# Start BE
docker run -d --name starrocks-be \
  --privileged=true \
  -p 9050:9050 -p 9060:9060 -p 9070:9070 \
  starrocks/be-ubuntu:3.3-latest \
  /bin/bash -c "bash /opt/starrocks/be/bin/start_backend.sh --daemon && sleep 3 && tail -f /opt/starrocks/be/log/be.out"

# Register BE
BE_IP=$(docker inspect starrocks-be --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
mysql -h 127.0.0.1 -P 9030 -u root -p'datax_test' -e "ALTER SYSTEM ADD BACKEND '${BE_IP}:9050'"

# Verify
mysql -h 127.0.0.1 -P 9030 -u root -p'datax_test' -e "SHOW BACKENDS"
```

## 2. Create Test Data

```bash
mysql -h 127.0.0.1 -P 9030 -u root -p'datax_test' <<'EOF'
CREATE DATABASE IF NOT EXISTS test_db;
USE test_db;
CREATE TABLE IF NOT EXISTS test_tbl (
    id INT,
    name VARCHAR(50),
    score DOUBLE,
    active BOOLEAN
) DISTRIBUTED BY HASH(id) BUCKETS 1
  PROPERTIES ('replication_num' = '1');

INSERT INTO test_tbl VALUES
    (1, 'Alice', 88.5, true),
    (2, 'Bob', 92.3, true),
    (3, 'Charlie', 75.0, false),
    (4, 'Diana', 95.8, true),
    (5, 'Eve', 68.5, false);
EOF
```

## 3. Job Configuration

Create `/tmp/job_starrocksreader.json`:

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
                    "name": "starrocksreader",
                    "parameter": {
                        "username": "root",
                        "password": "datax_test",
                        "connection": [
                            {
                                "jdbcUrl": ["jdbc:mysql://127.0.0.1:9030/test_db"],
                                "table": ["test_tbl"]
                            }
                        ],
                        "column": ["*"],
                        "splitPk": "id"
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

### Parameter Description (inherited from CommonRdbmsReader)

| Parameter | Required | Description |
|-----------|----------|-------------|
| `username` | Yes | StarRocks user |
| `password` | Yes | StarRocks password (required, cannot be empty) |
| `connection[].jdbcUrl[]` | Yes | JDBC URL array, format: `jdbc:mysql://host:9030/db` |
| `connection[].table[]` | Yes | Table name array |
| `column` | Yes | Column list (`["*"]` for all, or explicit list) |
| `where` | No | WHERE condition for row filtering |
| `splitPk` | No | Split key for parallel reading (use primary key column) |
| `fetchSize` | No | JDBC fetch size (default: auto) |

## 4. Run DataX

```bash
python $DATAX_HOME/bin/datax.py /tmp/job_starrocksreader.json
```

Expected output:

```
1	Alice	88.5	true
2	Bob	92.3	true
3	Charlie	75	false
4	Diana	95.8	true
5	Eve	68.5	false
...
读出记录总数                    :                   5
读写失败总数                    :                   0
```

## 5. Cleanup

```bash
docker stop starrocks-fe starrocks-be && docker rm starrocks-fe starrocks-be
```

## 6. Notes

- Uses JDBC via MySQL protocol (port 9030) — identical to `mysqlreader` in behavior.
- `DataBaseType.StarRocks` handles StarRocks-specific SQL dialect (no `mysql` system database).
- `password` field is **required** by the RDBMS framework and cannot be blank. Set a password via `ALTER USER 'root' IDENTIFIED BY 'xxx'` if needed.
- No dedicated `plugin_job_template.json` or doc exists for the reader — this doc fills the gap.
- The reader delegates all logic to `CommonRdbmsReader` from `plugin-rdbms-util`.

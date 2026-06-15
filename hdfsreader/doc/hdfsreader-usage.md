# HdfsReader 使用文档

## 1. HDFS Docker 环境搭建

```bash
# 启动 Hadoop 容器（单节点 HDFS）
docker run -d --name hadoop --hostname hadoop \
  -p 9870:9870 -p 9000:9000 -p 9864:9864 \
  apache/hadoop:3 \
  /bin/bash -c "tail -f /dev/null"

# 配置 HDFS
docker exec --user root hadoop bash -c '
cat > /opt/hadoop/etc/hadoop/core-site.xml << "EOF"
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://hadoop:9000</value>
    </property>
</configuration>
EOF

cat > /opt/hadoop/etc/hadoop/hdfs-site.xml << "EOF"
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property>
        <name>dfs.namenode.name.dir</name>
        <value>/opt/hadoop/data/nameNode</value>
    </property>
    <property>
        <name>dfs.datanode.data.dir</name>
        <value>/opt/hadoop/data/dataNode</value>
    </property>
    <property>
        <name>dfs.replication</name>
        <value>1</value>
    </property>
    <property>
        <name>dfs.namenode.http-address</name>
        <value>0.0.0.0:9870</value>
    </property>
    <property>
        <name>dfs.permissions.enabled</name>
        <value>false</value>
    </property>
</configuration>
EOF

mkdir -p /opt/hadoop/data/nameNode /opt/hadoop/data/dataNode
chown -R hadoop:users /opt/hadoop/data
'

# 格式化并启动 HDFS
docker exec --user hadoop hadoop bash -c '
export HDFS_NAMENODE_USER=hadoop
export HDFS_DATANODE_USER=hadoop
export HDFS_SECONDARYNAMENODE_USER=hadoop
export JAVA_HOME=/usr/lib/jvm/jre
/opt/hadoop/bin/hdfs namenode -format -force -nonInteractive
/opt/hadoop/bin/hdfs --daemon start namenode
/opt/hadoop/bin/hdfs --daemon start datanode
'

# 验证
docker exec --user hadoop hadoop /opt/hadoop/bin/hdfs dfsadmin -report | head -5
```

## 2. 创建测试数据

```bash
# 获取 HDFS 容器 IP
HDFS_IP=$(docker inspect hadoop --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "HDFS IP: $HDFS_IP"

# 创建 TEXT 测试文件
docker exec --user hadoop hadoop bash -c '
echo -e "1\tAlice\t95.5\n2\tBob\t87.0\n3\tCharlie\t91.3" > /tmp/test_data.txt
/opt/hadoop/bin/hdfs dfs -mkdir -p /data/test
/opt/hadoop/bin/hdfs dfs -put /tmp/test_data.txt /data/test/
'
```

## 3. 提交 DataX 任务

### TEXT 文件读取

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "hdfsreader",
          "parameter": {
            "path": "/data/test/*",
            "defaultFS": "hdfs://<hdfs_ip>:9000",
            "column": [
              {"index": 0, "type": "long"},
              {"index": 1, "type": "string"},
              {"index": 2, "type": "double"}
            ],
            "fileType": "text",
            "encoding": "UTF-8",
            "fieldDelimiter": "\t"
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": { "print": true }
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

### ORC 文件读取

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "hdfsreader",
          "parameter": {
            "path": "/data/orc_output/*",
            "defaultFS": "hdfs://<hdfs_ip>:9000",
            "column": [
              {"index": 0, "type": "long"},
              {"index": 1, "type": "string"},
              {"index": 2, "type": "double"}
            ],
            "fileType": "orc",
            "encoding": "UTF-8"
          }
        },
        "writer": {
          "name": "streamwriter",
          "parameter": { "print": true }
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
| `path` | 是 | HDFS 文件路径，支持 `*` 和 `?` 通配符 |
| `defaultFS` | 是 | HDFS NameNode 地址，格式 `hdfs://host:port` |
| `fileType` | 是 | 文件类型：`text`, `orc`, `rc`, `seq`, `csv` |
| `column` | 是 | 列定义，每个元素包含 `index`+`type` 或 `value` |
| `fieldDelimiter` | 否 | 字段分隔符，TEXT/CSV 需要，ORC 忽略 |
| `encoding` | 否 | 编码，默认 `UTF-8` |
| `nullFormat` | 否 | null 值表示 |
| `compress` | 否 | 压缩类型 |
| `hadoopConfig` | 否 | 高级 Hadoop 配置（如 HA 设置） |
| `haveKerberos` | 否 | 是否使用 Kerberos，默认 false |
| `kerberosKeytabFilePath` | 否 | Keytab 文件路径 |
| `kerberosPrincipal` | 否 | Kerberos Principal |

## 5. 类型映射

| DataX 内部类型 | Hive/HDFS 类型 |
|---------------|----------------|
| Long | TINYINT, SMALLINT, INT, BIGINT |
| Double | FLOAT, DOUBLE, DECIMAL |
| String | STRING, VARCHAR, CHAR |
| Boolean | BOOLEAN |
| Date | DATE, TIMESTAMP |
| Bytes | BINARY |

## 6. 验证结果

TEXT 读取输出：
```
1	Alice	95.5
2	Bob	87.0
3	Charlie	91.3
读出记录总数: 3
```

ORC 读取输出：
```
100	datax_test	88.5
读出记录总数: 1
```

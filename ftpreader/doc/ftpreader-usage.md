# FtpReader 使用文档

## 1. FTP 服务器 Docker 环境搭建

```bash
# 启动 vsftpd 容器（PASV 模式端口 30000-30009）
docker run -d --name vsftpd \
  -p 21:21 -p 30000-30009:30000-30009 \
  -e FTP_USER=datax \
  -e FTP_PASS=datax123 \
  -e PASV_MIN_PORT=30000 \
  -e PASV_MAX_PORT=30009 \
  -e PASV_ADDRESS=127.0.0.1 \
  fauria/vsftpd

# 验证连接
curl -s ftp://127.0.0.1/ --user datax:datax123
```

## 2. 创建测试文件

```bash
# 创建 CSV 测试数据
cat > /tmp/test_data.csv << EOF
id,name,score,active
1,Alice,95.5,true
2,Bob,87.0,false
3,Charlie,91.3,true
4,Diana,78.6,true
5,Eve,88.9,false
EOF

# 上传到 FTP 服务器
curl -s -T /tmp/test_data.csv ftp://127.0.0.1/ --user datax:datax123
```

## 3. 提交 DataX 任务

### 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "ftpreader",
          "parameter": {
            "protocol": "ftp",
            "host": "127.0.0.1",
            "port": 21,
            "username": "datax",
            "password": "datax123",
            "path": "/test_data.csv",
            "column": [
              { "index": 0, "type": "long" },
              { "index": 1, "type": "string" },
              { "index": 2, "type": "double" },
              { "index": 3, "type": "boolean" }
            ],
            "fieldDelimiter": ",",
            "encoding": "UTF-8",
            "skipHeader": true,
            "fileFormat": "csv"
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

### 运行

```bash
python datax/bin/datax.py job_ftpreader.json
```

## 4. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `protocol` | 是 | - | `"ftp"` 或 `"sftp"` |
| `host` | 是 | - | FTP/SFTP 服务器地址 |
| `port` | 否 | `21`(ftp) / `22`(sftp) | 端口 |
| `username` | 是 | - | 登录用户名 |
| `password` | 是 | - | 登录密码 |
| `timeout` | 否 | `60000` | 超时时间（毫秒） |
| `connectPattern` | 否 | `"PASV"` | FTP 模式：`"PORT"`（主动）或 `"PASV"`（被动） |
| `path` | 是 | - | 文件路径，支持 `*`/`?` 通配符和目录递归 |
| `column` | 是 | `["*"]` | 列定义：`index`（位置）或 `value`（常量），支持 `long`/`string`/`double`/`boolean`/`date` |
| `fieldDelimiter` | 是 | `","` | 列分隔符（单字符） |
| `encoding` | 否 | `"UTF-8"` | 文件编码 |
| `skipHeader` | 否 | `false` | 是否跳过首行（表头） |
| `nullFormat` | 否 | `\N` | 代表 null 的字符串 |
| `compress` | 否 | - | 压缩格式：`gzip`/`bzip2`/`zip`/`lzo`/`lzo_deflate`/`hadoop-snappy`/`framing-snappy` |
| `fileFormat` | 否 | `"csv"` | 文件格式：`csv`/`text`/`excel`/`binary` |
| `bufferSize` | 否 | `8192` | 缓冲区大小 |
| `maxTraversalLevel` | 否 | `100` | 目录递归最大深度 |
| `skipEmptyRecords` | 否 | `true` | 是否跳过空行 |
| `csvReaderConfig` | 否 | - | CSV 解析器配置（JSON） |

## 5. 注意事项

- `path` 支持通配符 `*`（匹配任意字符）和 `?`（匹配单个字符），仅在最后一级路径生效
- `path` 可以是路径数组，读取多个文件
- 不支持 FTPS（FTP over SSL/TLS）
- SFTP 默认关闭 StrictHostKeyChecking
- 不支持符号链接（symlink）
- 单个文件不会被切分到多个 channel

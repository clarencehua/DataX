# FtpWriter 使用文档

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

## 2. 创建输出目录

```bash
curl -s --ftp-create-dirs ftp://127.0.0.1/output/ --user datax:datax123
```

## 3. 提交 DataX 任务

### 配置示例

```json
{
  "job": {
    "content": [
      {
        "reader": {
          "name": "streamreader",
          "parameter": {
            "column": [
              { "type": "long", "value": 100 },
              { "type": "string", "value": "hello" },
              { "type": "double", "value": 3.14 }
            ],
            "sliceRecordCount": 1
          }
        },
        "writer": {
          "name": "ftpwriter",
          "parameter": {
            "protocol": "ftp",
            "host": "127.0.0.1",
            "port": 21,
            "username": "datax",
            "password": "datax123",
            "path": "/output",
            "fileName": "writer_test",
            "writeMode": "truncate",
            "fieldDelimiter": ",",
            "encoding": "UTF-8",
            "fileFormat": "text",
            "suffix": ".csv"
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
python datax/bin/datax.py job_ftpwriter.json
```

## 4. 验证数据

```bash
# 列出输出目录
curl -s ftp://127.0.0.1/output/ --user datax:datax123

# 查看文件内容
curl -s ftp://127.0.0.1/output/writer_test_xxx.csv --user datax:datax123
```

## 5. 参数说明

| 参数 | 必须 | 默认值 | 说明 |
|------|------|--------|------|
| `protocol` | 是 | - | `"ftp"` 或 `"sftp"` |
| `host` | 是 | - | FTP/SFTP 服务器地址 |
| `port` | 否 | `21`(ftp) / `22`(sftp) | 端口 |
| `username` | 是 | - | 登录用户名 |
| `password` | 是 | - | 登录密码 |
| `timeout` | 否 | `60000` | 超时时间（毫秒） |
| `path` | 是 | - | 输出目录路径 |
| `fileName` | 是 | - | 输出文件前缀（DataX 自动追加 UUID 后缀） |
| `writeMode` | 是 | - | 写入模式：`"truncate"`（覆盖）/ `"append"`（追加）/ `"nonConflict"`（冲突报错） |
| `fieldDelimiter` | 否 | `","` | 列分隔符（单字符） |
| `encoding` | 否 | `"UTF-8"` | 文件编码 |
| `nullFormat` | 否 | `\N` | null 值输出格式 |
| `dateFormat` | 否 | - | 日期格式 |
| `fileFormat` | 否 | `"text"` | 文件格式：`text`（简单分隔符）/ `csv`（严格 CSV）/ `sql`（SQL INSERT） |
| `suffix` | 否 | `""` | 文件后缀（如 `.csv`） |
| `header` | 否 | - | 写入首行的列名数组 |
| `compress` | 否 | - | 压缩格式：`gzip` / `bzip2` |
| `quoteChar` | 否 | - | SQL 格式的引号字符 |
| `maxFileSize` | 否 | - | 文件大小上限 |
| `commitSize` | 否 | `2000` | SQL 格式的提交行数 |

## 6. 写入模式说明

| writeMode | 行为 |
|-----------|------|
| `truncate` | 写入前删除 `path/` 下匹配 `fileName` 的文件 |
| `append` | 直接追加写入 |
| `nonConflict` | 如果文件已存在则报错 |

## 7. 注意事项

- 输出文件名格式：`{fileName}__{UUID}{suffix}`（UUID 中 `-` 替换为 `_`）
- 不支持 FTPS（FTP over SSL/TLS）
- SFTP 默认关闭 StrictHostKeyChecking
- 多个 channel 时每个 channel 写入一个独立文件

# PhotoBackup 服务端

与 Android 端 `CloudBackupApi` 对应的 HTTP 服务：使用 Golang 实现，SQLite 存储元数据，文件存于本地目录。

## API

- **PUT /api/file/upload**  
  - Header: `userid`, `category`, `sha1sum`，可选 `filename`（不传则用 sha1sum 作为文件名）  
  - Body: 文件内容 `application/octet-stream`

- **GET /api/file/download**  
  - Header: `userid`, `category`, `filename`  
  - Response: 文件内容

## 运行

```bash
# 编译
go build -o photobackup-server ./cmd/server

# 默认监听 :8080，数据目录 ./data
./photobackup-server

# 自定义
ADDR=:9000 DATA_DIR=/var/lib/photobackup ./photobackup-server
```

环境变量：

- `ADDR`：监听地址，默认 `:8080`
- `DATA_DIR`：数据目录（内含 SQLite 库与 files 子目录），默认 `./data`

## 数据

- SQLite：`DATA_DIR/photobackup.db`，表 `cloud_files` 存 user_id、category、filename、sha1_sum、storage_path、size、created_at。
- 文件：`DATA_DIR/files/{userid}/{category}/{sha1sum}`，按 sha1 存一份，多文件名通过数据库映射。

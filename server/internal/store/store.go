package store

import (
	"database/sql"
	"fmt"
	"path/filepath"

	_ "modernc.org/sqlite"
)

// FileRecord 云端文件记录，与 app BackedUpPhoto 对应
type FileRecord struct {
	ID         int64
	UserID     string
	Category   string
	Filename   string
	Sha1Sum    string
	StoragePath string
	Size       int64
	CreatedAt  int64
}

type Store struct {
	db    *sql.DB
	baseDir string
}

func New(dbPath, storageBaseDir string) (*Store, error) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping sqlite: %w", err)
	}
	s := &Store{db: db, baseDir: storageBaseDir}
	if err := s.migrate(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) migrate() error {
	_, err := s.db.Exec(`
		CREATE TABLE IF NOT EXISTS cloud_files (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			user_id TEXT NOT NULL,
			category TEXT NOT NULL,
			filename TEXT NOT NULL,
			sha1_sum TEXT NOT NULL,
			storage_path TEXT NOT NULL,
			size INTEGER NOT NULL,
			created_at INTEGER NOT NULL,
			UNIQUE(user_id, category, filename)
		);
		CREATE INDEX IF NOT EXISTS idx_cloud_files_user_category ON cloud_files(user_id, category);
	`)
	return err
}

// StoragePath 返回文件在磁盘上的存储路径（不创建目录）
func (s *Store) StoragePath(userID, category, sha1Sum string) string {
	return filepath.Join(s.baseDir, userID, category, sha1Sum)
}

// SaveFile 保存或更新文件记录
func (s *Store) SaveFile(userID, category, filename, sha1Sum, storagePath string, size int64) error {
	_, err := s.db.Exec(`
		INSERT INTO cloud_files (user_id, category, filename, sha1_sum, storage_path, size, created_at)
		VALUES (?, ?, ?, ?, ?, ?, strftime('%s','now'))
		ON CONFLICT(user_id, category, filename) DO UPDATE SET
			sha1_sum = excluded.sha1_sum,
			storage_path = excluded.storage_path,
			size = excluded.size,
			created_at = strftime('%s','now')
	`, userID, category, filename, sha1Sum, storagePath, size)
	return err
}

// GetByUserCategoryFilename 按 userid + category + filename 查询
func (s *Store) GetByUserCategoryFilename(userID, category, filename string) (*FileRecord, error) {
	var r FileRecord
	err := s.db.QueryRow(`
		SELECT id, user_id, category, filename, sha1_sum, storage_path, size, created_at
		FROM cloud_files WHERE user_id = ? AND category = ? AND filename = ?
	`, userID, category, filename).Scan(&r.ID, &r.UserID, &r.Category, &r.Filename, &r.Sha1Sum, &r.StoragePath, &r.Size, &r.CreatedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &r, nil
}

// GetByUserCategoryFileId 按 userid + category + fileId(sha1) 查询
func (s *Store) GetByUserCategoryFileId(userID, category, fileId string) (*FileRecord, error) {
	var r FileRecord
	err := s.db.QueryRow(`
		SELECT id, user_id, category, filename, sha1_sum, storage_path, size, created_at
		FROM cloud_files WHERE user_id = ? AND category = ? AND sha1_sum = ?
	`, userID, category, fileId).Scan(&r.ID, &r.UserID, &r.Category, &r.Filename, &r.Sha1Sum, &r.StoragePath, &r.Size, &r.CreatedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &r, nil
}

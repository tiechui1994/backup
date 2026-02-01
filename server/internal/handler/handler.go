package handler

import (
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"

	"photobackup/server/internal/store"
)

// File 文件上传/下载处理
type File struct {
	Store *store.Store
}

// Upload 处理 PUT /api/file/upload
// Header: userid, category, sha1sum
// Body: 文件内容 application/octet-stream
// 成功响应: {"fileId":"<sha1sum>"}
func (h *File) Upload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	userID := r.Header.Get("userid")
	category := r.Header.Get("category")
	sha1Sum := r.Header.Get("sha1sum")
	if userID == "" || category == "" || sha1Sum == "" {
		http.Error(w, "missing header: userid, category or sha1sum", http.StatusBadRequest)
		return
	}

	storagePath := h.Store.StoragePath(userID, category, sha1Sum)
	dir := filepath.Dir(storagePath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		http.Error(w, "failed to create storage dir", http.StatusInternalServerError)
		return
	}
	f, err := os.Create(storagePath)
	if err != nil {
		http.Error(w, "failed to create file", http.StatusInternalServerError)
		return
	}
	defer f.Close()
	n, err := io.Copy(f, r.Body)
	if err != nil {
		os.Remove(storagePath)
		http.Error(w, "failed to write file", http.StatusInternalServerError)
		return
	}
	// 存库时 filename 用 sha1sum（上传接口不传文件名）
	if err := h.Store.SaveFile(userID, category, sha1Sum, sha1Sum, storagePath, n); err != nil {
		os.Remove(storagePath)
		http.Error(w, "failed to save record", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"fileId": sha1Sum})
}

// Download 处理 GET /api/file/download?fileId=xxx
// Header: userid, category, filename（用于 Content-Disposition）
// Response: 文件内容
func (h *File) Download(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	fileId := r.URL.Query().Get("fileId")
	userID := r.Header.Get("userid")
	category := r.Header.Get("category")
	filename := r.Header.Get("filename")
	if fileId == "" || userID == "" || category == "" || filename == "" {
		http.Error(w, "missing query fileId or header: userid, category or filename", http.StatusBadRequest)
		return
	}

	rec, err := h.Store.GetByUserCategoryFileId(userID, category, fileId)
	if err != nil {
		http.Error(w, "database error", http.StatusInternalServerError)
		return
	}
	if rec == nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}

	f, err := os.Open(rec.StoragePath)
	if err != nil {
		if os.IsNotExist(err) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "failed to open file", http.StatusInternalServerError)
		return
	}
	defer f.Close()
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename="+filename)
	io.Copy(w, f)
}

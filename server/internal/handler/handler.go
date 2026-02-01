package handler

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"

	"photobackup/server/internal/store"
)

// File 文件上传/下载处理
type File struct {
	Store *store.Store
}

// Upload 处理 PUT /api/file/upload
// Header: userid, category, sha1sum, filename
// Body: 文件内容 application/octet-stream
// 成功响应: {"fileId":"<sha1sum>"}
func (h *File) Upload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut {
		log.Printf("[upload] method not allowed: %s", r.Method)
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	userID := r.Header.Get("userid")
	category, _ := url.QueryUnescape(r.Header.Get("category"))
	sha1Sum := r.Header.Get("sha1sum")
	filename, _ := url.QueryUnescape(r.Header.Get("filename"))
	if userID == "" || category == "" || sha1Sum == "" || filename == "" {
		log.Printf("[upload] missing header: userid=%q category=%q sha1sum=%q filename=%q", userID, category, sha1Sum, filename)
		http.Error(w, "missing header: userid, category, sha1sum or filename", http.StatusBadRequest)
		return
	}

	storagePath := h.Store.StoragePath(userID, category, sha1Sum)
	dir := filepath.Dir(storagePath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		log.Printf("[upload] failed to create storage dir %s: %v", dir, err)
		http.Error(w, "failed to create storage dir", http.StatusInternalServerError)
		return
	}
	f, err := os.Create(storagePath)
	if err != nil {
		log.Printf("[upload] failed to create file %s: %v", storagePath, err)
		http.Error(w, "failed to create file", http.StatusInternalServerError)
		return
	}
	defer f.Close()
	n, err := io.Copy(f, r.Body)
	if err != nil {
		os.Remove(storagePath)
		log.Printf("[upload] failed to write file %s: %v", storagePath, err)
		http.Error(w, "failed to write file", http.StatusInternalServerError)
		return
	}
	if err := h.Store.SaveFile(userID, category, filename, sha1Sum, storagePath, n); err != nil {
		os.Remove(storagePath)
		log.Printf("[upload] failed to save record userid=%s category=%s filename=%s: %v", userID, category, filename, err)
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
		log.Printf("[download] method not allowed: %s", r.Method)
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	fileId := r.URL.Query().Get("fileId")
	userID := r.Header.Get("userid")
	category, _ := url.QueryUnescape(r.Header.Get("category"))
	if fileId == "" || userID == "" || category == "" {
		log.Printf("[download] missing query or header: fileId=%q userid=%q category=%q", fileId, userID, category)
		http.Error(w, "missing query fileId or header: userid, category", http.StatusBadRequest)
		return
	}

	rec, err := h.Store.GetByUserCategoryFileId(userID, category, fileId)
	if err != nil {
		log.Printf("[download] database error userid=%s category=%s fileId=%s: %v", userID, category, fileId, err)
		http.Error(w, "database error", http.StatusInternalServerError)
		return
	}
	if rec == nil {
		log.Printf("[download] not found: userid=%s category=%s fileId=%s", userID, category, fileId)
		http.Error(w, "not found", http.StatusNotFound)
		return
	}

	f, err := os.Open(rec.StoragePath)
	if err != nil {
		if os.IsNotExist(err) {
			log.Printf("[download] file not on disk: path=%s userid=%s fileId=%s", rec.StoragePath, userID, fileId)
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		log.Printf("[download] failed to open file %s: %v", rec.StoragePath, err)
		http.Error(w, "failed to open file", http.StatusInternalServerError)
		return
	}
	defer f.Close()
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename="+rec.Filename)
	if _, err := io.Copy(w, f); err != nil {
		log.Printf("[download] failed to write response userid=%s fileId=%s: %v", userID, fileId, err)
	}
}

package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"

	"photobackup/server/internal/handler"
	"photobackup/server/internal/store"
)

func main() {
	dataDir := os.Getenv("DATA_DIR")
	if dataDir == "" {
		dataDir = "./data"
	}
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		log.Fatal("mkdir data dir: ", err)
	}
	dbPath := filepath.Join(dataDir, "photobackup.db")
	storageDir := filepath.Join(dataDir, "files")

	st, err := store.New(dbPath, storageDir)
	if err != nil {
		log.Fatal("store: ", err)
	}
	defer st.Close()

	addr := os.Getenv("ADDR")
	if addr == "" {
		addr = ":8080"
	}

	fileHandler := &handler.File{Store: st}
	http.HandleFunc("/api/file/upload", fileHandler.Upload)
	http.HandleFunc("/api/file/download", fileHandler.Download)

	log.Println("server listen on", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatal("listen: ", err)
	}
}

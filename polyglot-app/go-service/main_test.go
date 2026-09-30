package main

import (
	"bytes"
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestHealth(t *testing.T) {
	w := httptest.NewRecorder()
	_ = w
}

func TestProcessValidCSV(t *testing.T) {
	payload, _ := json.Marshal(ProcessRequest{Data: "name,amount\nAlice,10\nBob,20"})
	req := httptest.NewRequest("POST", "/process", bytes.NewBuffer(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	processHandler(w, req)
	if w.Code != 200 {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var body ProcessResponse
	_ = json.NewDecoder(w.Body).Decode(&body)
	if body.Rows != 2 {
		t.Fatalf("expected 2 rows, got %d", body.Rows)
	}
}

func TestProcessInvalidCSV(t *testing.T) {
	payload, _ := json.Marshal(ProcessRequest{Data: "\"name\",\"amount\"\n\"Alice\",10\n\"Bob\",\"20"})
	req := httptest.NewRequest("POST", "/process", bytes.NewBuffer(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	processHandler(w, req)
	if w.Code != 400 {
		t.Fatalf("expected 400, got %d", w.Code)
	}
	var body ProcessResponse
	_ = json.NewDecoder(w.Body).Decode(&body)
	if body.Error == "" {
		t.Fatal("expected error for invalid CSV")
	}
}

func TestDecodeProcessRequestFromJSON(t *testing.T) {
	var req ProcessRequest
	if err := json.NewDecoder(bytes.NewBufferString(`{"data":"a,b"}`)).Decode(&req); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if req.Data != "a,b" {
		t.Fatalf("unexpected data: %s", req.Data)
	}
}

func TestImportCSVValid(t *testing.T) {
	payload, _ := json.Marshal(map[string]interface{}{"rows": []interface{}{
		map[string]interface{}{"account_id": "1", "amount": 10, "description": "a", "date": "2026-01-01"},
		map[string]interface{}{"account_id": "2", "amount": 20, "description": "b", "date": "2026-01-02"},
	}})
	req := httptest.NewRequest("POST", "/api/csv/import", bytes.NewBuffer(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	importCSVHandler(w, req)
	if w.Code != 200 {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var body map[string]interface{}
	_ = json.NewDecoder(w.Body).Decode(&body)
	if body["created"] != float64(2) {
		t.Fatalf("expected created=2, got %v", body["created"])
	}
}

func TestImportCSVMissingRequired(t *testing.T) {
	payload, _ := json.Marshal(map[string]interface{}{"rows": []interface{}{
		map[string]interface{}{"account_id": "", "amount": 10, "description": "a", "date": "2026-01-01"},
	}})
	req := httptest.NewRequest("POST", "/api/csv/import", bytes.NewBuffer(payload))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	importCSVHandler(w, req)
	if w.Code != 200 {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var body map[string]interface{}
	_ = json.NewDecoder(w.Body).Decode(&body)
	errors, _ := body["errors"].([]interface{})
	if len(errors) == 0 {
		t.Fatal("expected errors for missing required fields")
	}
}

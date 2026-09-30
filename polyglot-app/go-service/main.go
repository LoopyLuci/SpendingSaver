package main

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
)

type ProcessRequest struct {
	Data string `json:"data"`
}

type ProcessResponse struct {
	Rows   int      `json:"rows"`
	Header []string `json:"header"`
	Preview [][]string `json:"preview"`
	Error  string   `json:"error,omitempty"`
}

var inMemoryStore = struct {
	accounts      map[string]map[string]interface{}
	categories    map[string]map[string]interface{}
	transactions  map[string]map[string]interface{}
	budgets       map[string]map[string]interface{}
	nextIDs       map[string]int
	mu            sync.RWMutex
}{accounts: map[string]map[string]interface{}{}, categories: map[string]map[string]interface{}{}, transactions: map[string]map[string]interface{}{}, budgets: map[string]map[string]interface{}{}, nextIDs: map[string]int{"account": 1, "category": 1, "transaction": 1, "budget": 1}}

func listAccounts() ([]map[string]interface{}, error) {
	inMemoryStore.mu.RLock()
	defer inMemoryStore.mu.RUnlock()
	out := make([]map[string]interface{}, 0, len(inMemoryStore.accounts))
	for _, v := range inMemoryStore.accounts {
		out = append(out, v)
	}
	return out, nil
}

func createAccount(payload map[string]interface{}) (map[string]interface{}, error) {
	inMemoryStore.mu.Lock()
	defer inMemoryStore.mu.Unlock()
	id := strconv.Itoa(inMemoryStore.nextIDs["account"])
	inMemoryStore.nextIDs["account"]++
	rec := map[string]interface{}{"id": id}
	for k, v := range payload {
		rec[k] = v
	}
	inMemoryStore.accounts[id] = rec
	return rec, nil
}

func listTransactions() ([]map[string]interface{}, error) {
	inMemoryStore.mu.RLock()
	defer inMemoryStore.mu.RUnlock()
	out := make([]map[string]interface{}, 0, len(inMemoryStore.transactions))
	for _, v := range inMemoryStore.transactions {
		out = append(out, v)
	}
	return out, nil
}

func createTransaction(payload map[string]interface{}) (map[string]interface{}, error) {
	inMemoryStore.mu.Lock()
	defer inMemoryStore.mu.Unlock()
	id := strconv.Itoa(inMemoryStore.nextIDs["transaction"])
	inMemoryStore.nextIDs["transaction"]++
	rec := map[string]interface{}{"id": id}
	for k, v := range payload {
		rec[k] = v
	}
	inMemoryStore.transactions[id] = rec
	return rec, nil
}

func listCategories() ([]map[string]interface{}, error) {
	inMemoryStore.mu.RLock()
	defer inMemoryStore.mu.RUnlock()
	out := make([]map[string]interface{}, 0, len(inMemoryStore.categories))
	for _, v := range inMemoryStore.categories {
		out = append(out, v)
	}
	return out, nil
}

func createCategory(payload map[string]interface{}) (map[string]interface{}, error) {
	inMemoryStore.mu.Lock()
	defer inMemoryStore.mu.Unlock()
	id := strconv.Itoa(inMemoryStore.nextIDs["category"])
	inMemoryStore.nextIDs["category"]++
	rec := map[string]interface{}{"id": id}
	for k, v := range payload {
		rec[k] = v
	}
	inMemoryStore.categories[id] = rec
	return rec, nil
}

func processHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	var req ProcessRequest
	if err := decodeJSON(r, &req); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		writeJSON(w, ProcessResponse{Error: "invalid json"})
		return
	}
	reader := csv.NewReader(strings.NewReader(req.Data))
	records, err := reader.ReadAll()
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		writeJSON(w, ProcessResponse{Error: err.Error()})
		return
	}
	if len(records) == 0 {
		writeJSON(w, ProcessResponse{Rows: 0, Header: []string{}})
		return
	}
	header := records[0]
	preview := records
	if len(preview) > 5 {
		preview = preview[:5]
	}
	writeJSON(w, ProcessResponse{Rows: len(records) - 1, Header: header, Preview: preview})
}

func importCSVHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		writeJSON(w, map[string]string{"error": "method not allowed"})
		return
	}
	var req map[string]any
	if err := decodeJSON(r, &req); err != nil {
		w.WriteHeader(http.StatusBadRequest)
		writeJSON(w, map[string]string{"error": "invalid json"})
		return
	}
	rows, ok := req["rows"].([]interface{})
	if !ok || len(rows) == 0 {
		w.WriteHeader(http.StatusBadRequest)
		writeJSON(w, map[string]string{"error": "no rows provided"})
		return
	}
	required := []string{"account_id", "amount", "description", "date"}
	created := 0
	errors := []map[string]interface{}{}
	for idx, raw := range rows {
		row, ok := raw.(map[string]interface{})
		if !ok {
			errors = append(errors, map[string]interface{}{"row": idx, "error": "invalid row"})
			continue
		}
		missing := []string{}
		for _, field := range required {
			v := row[field]
			if v == nil || v == "" {
				missing = append(missing, field)
			}
		}
		if len(missing) > 0 {
			errors = append(errors, map[string]interface{}{"row": idx, "error": "missing " + strings.Join(missing, ", ")})
			continue
		}
		if _, err := strconv.ParseFloat(fmt.Sprintf("%v", row["amount"]), 64); err != nil {
			errors = append(errors, map[string]interface{}{"row": idx, "error": "amount not numeric"})
			continue
		}
		created++
	}
	writeJSON(w, map[string]interface{}{"created": created, "errors": errors})
}

func main() {
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"ok","service":"spendingsaver-go"}`))
	})
	http.HandleFunc("/api/accounts", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.Method {
		case "GET":
			out, _ := listAccounts()
			writeJSON(w, out)
		case "POST":
			payload := readJSON(r)
			out, _ := createAccount(payload)
			writeJSON(w, out)
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	})
	http.HandleFunc("/api/transactions", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.Method {
		case "GET":
			out, _ := listTransactions()
			writeJSON(w, out)
		case "POST":
			payload := readJSON(r)
			out, _ := createTransaction(payload)
			writeJSON(w, out)
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	})
	http.HandleFunc("/api/categories", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.Method {
		case "GET":
			out, _ := listCategories()
			writeJSON(w, out)
		case "POST":
			payload := readJSON(r)
			out, _ := createCategory(payload)
			writeJSON(w, out)
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	})
	http.HandleFunc("/process", processHandler)
	http.HandleFunc("/api/csv/import", importCSVHandler)
	http.ListenAndServe(":8126", nil)
}

func readJSON(r *http.Request) map[string]interface{} {
	var out map[string]interface{}
	_ = decodeJSON(r, &out)
	if out == nil {
		out = map[string]interface{}{}
	}
	return out
}

func decodeJSON(r *http.Request, v interface{}) error {
	defer r.Body.Close()
	dec := json.NewDecoder(r.Body)
	return dec.Decode(v)
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	enc := json.NewEncoder(w)
	_ = enc.Encode(v)
}

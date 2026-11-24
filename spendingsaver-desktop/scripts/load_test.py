"""
Load testing script for SpendingSaver backend.
Run with: python scripts/load_test.py
"""
import time
import threading
import statistics
import urllib.request
import json
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE_URL = "http://127.0.0.1:8125"
RESULTS = []
ERRORS = 0

def make_request(path: str, method: str = "GET", data: bytes = None) -> dict:
    global ERRORS
    url = f"{BASE_URL}{path}"
    try:
        req = urllib.request.Request(url, method=method)
        if data:
            req.add_header("Content-Type", "application/json")
            req.data = data
        
        start = time.time()
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode()
            elapsed = time.time() - start
            return {"status": resp.status, "time": elapsed, "size": len(body)}
    except Exception as e:
        ERRORS += 1
        return {"status": 0, "time": 0, "error": str(e)}

def test_health_concurrent(n: int = 50):
    print(f"Testing {n} concurrent health requests...")
    start = time.time()
    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = [executor.submit(make_request, "/health") for _ in range(n)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.time() - start
    times = [r["time"] for r in results if r["time"] > 0]
    print(f"  Completed in {elapsed:.2f}s")
    print(f"  Avg latency: {statistics.mean(times)*1000:.0f}ms")
    print(f"  P95 latency: {sorted(times)[int(len(times)*0.95)]*1000:.0f}ms")
    print(f"  Throughput: {n/elapsed:.0f} req/s")
    return {"test": "health_concurrent", "requests": n, "elapsed": elapsed, "avg_ms": statistics.mean(times)*1000}

def test_api_endpoints():
    print("Testing API endpoint latency...")
    endpoints = [
        "/health",
        "/api/accounts",
        "/api/categories",
        "/api/transactions",
        "/api/budgets",
        "/api/recurring-transactions",
        "/api/exchange-rates?base=USD",
        "/api/net-worth",
        "/api/search?q=test",
    ]
    results = []
    for path in endpoints:
        times = []
        for _ in range(5):
            r = make_request(path)
            if r["time"] > 0:
                times.append(r["time"])
        if times:
            avg = statistics.mean(times)
            print(f"  {path}: {avg*1000:.0f}ms avg")
            results.append({"endpoint": path, "avg_ms": avg*1000})
    return results

def test_sustained_load(duration: int = 30):
    print(f"Testing sustained load for {duration}s...")
    start = time.time()
    request_count = 0
    
    def worker():
        nonlocal request_count
        while time.time() - start < duration:
            make_request("/health")
            request_count += 1
    
    threads = [threading.Thread(target=worker) for _ in range(5)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    
    elapsed = time.time() - start
    rps = request_count / elapsed
    print(f"  Total requests: {request_count}")
    print(f"  Duration: {elapsed:.1f}s")
    print(f"  Avg throughput: {rps:.0f} req/s")
    return {"test": "sustained_load", "duration": duration, "total_requests": request_count, "rps": rps}

def test_error_rate(n: int = 100):
    print(f"Testing error rate with {n} requests...")
    success = 0
    errors = 0
    for _ in range(n):
        r = make_request("/health")
        if r["status"] == 200:
            success += 1
        else:
            errors += 1
    error_rate = errors / n * 100
    print(f"  Success: {success}/{n}")
    print(f"  Error rate: {error_rate:.1f}%")
    return {"test": "error_rate", "total": n, "errors": errors, "error_rate": error_rate}

def run_all_tests():
    print("=" * 50)
    print("SpendingSaver Load Test")
    print("=" * 50)
    
    results = []
    
    results.append(test_health_concurrent(50))
    results.append(test_health_concurrent(100))
    results.append(test_health_concurrent(200))
    results.append(test_api_endpoints())
    results.append(test_sustained_load(10))
    results.append(test_error_rate(50))
    
    print("\n" + "=" * 50)
    print("Summary")
    print("=" * 50)
    
    with open("load_test_results.json", "w") as f:
        json.dump(results, f, indent=2)
    
    print("Results saved to load_test_results.json")
    
    rps_results = [r for r in results if isinstance(r, dict) and "rps" in r]
    if rps_results:
        avg_rps = statistics.mean([r["rps"] for r in rps_results])
        print(f"Average throughput: {avg_rps:.0f} req/s")
    
    return results

if __name__ == "__main__":
    run_all_tests()

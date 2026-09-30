import json
import os
import time
from typing import Dict, Optional

CACHE_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "exchange_rates_cache.json")
CACHE_TTL = 3600

def get_cached_rates() -> Optional[Dict]:
    try:
        if os.path.exists(CACHE_FILE):
            with open(CACHE_FILE) as f:
                data = json.load(f)
            if time.time() - data.get("timestamp", 0) < CACHE_TTL:
                return data.get("rates")
    except Exception:
        pass
    return None

def save_rates_cache(rates: Dict):
    try:
        os.makedirs(os.path.dirname(CACHE_FILE), exist_ok=True)
        with open(CACHE_FILE, "w") as f:
            json.dump({"rates": rates, "timestamp": time.time()}, f)
    except Exception:
        pass

def get_live_rates(base: str = "USD") -> Dict:
    cached = get_cached_rates()
    if cached:
        return cached
    try:
        import urllib.request
        url = f"https://api.exchangerate-api.com/v4/latest/{base}"
        req = urllib.request.Request(url, headers={"User-Agent": "SpendingSaver/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            rates = data.get("rates", {})
            save_rates_cache(rates)
            return rates
    except Exception:
        return {"USD": 1.0, "EUR": 0.92, "GBP": 0.79, "CAD": 1.36, "AUD": 1.53, "JPY": 149.50}

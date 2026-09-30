import re
from typing import Dict, Optional

def extract_receipt_data(text: str) -> Dict:
    result = {
        "merchant": None,
        "total": None,
        "date": None,
        "tax": None,
        "items": [],
    }
    lines = text.strip().split('\n')
    for i, line in enumerate(lines):
        line = line.strip()
        if not line:
            continue
        if not result["merchant"] and i < 3 and len(line) > 3 and not line[0].isdigit():
            result["merchant"] = line
        total_match = re.search(r'(?:total|amount|sum|grand\s*total)[:\s]*[\$]?([\d,]+\.?\d*)', line, re.IGNORECASE)
        if total_match:
            try:
                result["total"] = float(total_match.group(1).replace(',', ''))
            except ValueError:
                pass
        tax_match = re.search(r'(?:tax|vat|hst|gst)[:\s]*[\$]?([\d,]+\.?\d*)', line, re.IGNORECASE)
        if tax_match:
            try:
                result["tax"] = float(tax_match.group(1).replace(',', ''))
            except ValueError:
                pass
        date_match = re.search(r'(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})', line)
        if date_match and not result["date"]:
            result["date"] = date_match.group(1)
        item_match = re.match(r'(.+?)\s+[\$]?([\d,]+\.?\d*)$', line)
        if item_match and not any(kw in line.lower() for kw in ['total', 'tax', 'subtotal', 'change', 'cash', 'credit', 'debit']):
            try:
                amount = float(item_match.group(2).replace(',', ''))
                if amount > 0 and amount < 10000:
                    result["items"].append({"description": item_match.group(1).strip(), "amount": amount})
            except ValueError:
                pass
    return result

def parse_receipt_image_placeholder(image_bytes: bytes) -> Dict:
    return {
        "merchant": "Sample Merchant",
        "total": 0.0,
        "date": None,
        "tax": 0.0,
        "items": [],
        "note": "OCR requires external service integration"
    }

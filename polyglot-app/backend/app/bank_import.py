import re
from datetime import datetime
from typing import List, Dict

def parse_ofx(content: str) -> List[Dict]:
    transactions = []
    stmttrn_pattern = re.compile(r'<STMTTRN>(.*?)</STMTTRN>', re.DOTALL)
    for match in stmttrn_pattern.finditer(content):
        block = match.group(1)
        tx = {}
        for field in ['TRNTYPE', 'DTPOSTED', 'TRNAMT', 'NAME', 'MEMO', 'FITID']:
            m = re.search(rf'<{field}>(.*?)\n', block)
            if m:
                val = m.group(1).strip()
                if field == 'DTPOSTED':
                    val = val[:8]
                    try:
                        val = datetime.strptime(val, '%Y%m%d').strftime('%Y-%m-%d')
                    except ValueError:
                        pass
                elif field == 'TRNAMT':
                    try:
                        val = float(val)
                    except ValueError:
                        val = 0.0
                tx[field.lower()] = val
        if tx:
            transactions.append({
                'date': tx.get('dtposted', ''),
                'amount': tx.get('trnamt', 0.0),
                'description': tx.get('name', '') or tx.get('memo', ''),
                'reference': tx.get('fitid', ''),
                'type': tx.get('trntype', ''),
            })
    return transactions

def parse_qif(content: str) -> List[Dict]:
    transactions = []
    current = {}
    for line in content.split('\n'):
        line = line.strip()
        if line == '^':
            if current:
                transactions.append(current)
                current = {}
        elif line.startswith('D'):
            val = line[1:]
            for fmt in ['%m/%d/%Y', '%Y-%m-%d', '%d/%m/%Y']:
                try:
                    current['date'] = datetime.strptime(val, fmt).strftime('%Y-%m-%d')
                    break
                except ValueError:
                    continue
        elif line.startswith('T') or line.startswith('$'):
            try:
                current['amount'] = float(line[1:].replace(',', ''))
            except ValueError:
                pass
        elif line.startswith('P'):
            current['description'] = line[1:]
        elif line.startswith('N'):
            current['reference'] = line[1:]
        elif line.startswith('L'):
            current['category'] = line[1:]
    if current:
        transactions.append(current)
    return transactions

def parse_bank_file(filename: str, content: str) -> List[Dict]:
    lower = filename.lower()
    if lower.endswith('.ofx') or lower.endswith('.qfx'):
        return parse_ofx(content)
    elif lower.endswith('.qif'):
        return parse_qif(content)
    return []

"""
Automated accessibility audit for SpendingSaver frontend.
Run with: python scripts/a11y_audit.py
Requires: pip install axe-playwright-python (or use the API-based approach)
"""
import json
import sys
import urllib.request
from typing import List, Dict

FRONTEND_URL = "http://localhost:5173"

def check_meta_tags() -> List[Dict]:
    issues = []
    try:
        req = urllib.request.Request(FRONTEND_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            html = resp.read().decode()
            if 'lang=' not in html[:500]:
                issues.append({"severity": "serious", "description": "Missing lang attribute on <html>", "wcag": "3.1.1"})
            if '<meta name="viewport"' not in html:
                issues.append({"severity": "serious", "description": "Missing viewport meta tag", "wcag": "1.3.1"})
    except Exception as e:
        issues.append({"severity": "minor", "description": f"Could not fetch page: {e}", "wcag": ""})
    return issues

def check_color_contrast_static() -> List[Dict]:
    issues = []
    try:
        req = urllib.request.Request(f"{FRONTEND_URL}/assets/index.css", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            css = resp.read().decode()
            low_contrast_pairs = [
                ("text-gray-400", "on white", "4.5:1 minimum ratio"),
                ("text-gray-500", "on white", "4.5:1 minimum ratio"),
            ]
            for cls, bg, wcag_req in low_contrast_pairs:
                if cls in css:
                    issues.append({"severity": "notice", "description": f"{cls} may not meet {wcag_req}", "wcag": "1.4.3"})
    except:
        pass
    return issues

def check_keyboard_navigation() -> List[Dict]:
    issues = []
    try:
        req = urllib.request.Request(FRONTEND_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            html = resp.read().decode()
            if '<a ' not in html and '<button ' not in html:
                issues.append({"severity": "critical", "description": "No interactive elements found for keyboard navigation", "wcag": "2.1.1"})
            if 'tabindex' in html and 'tabindex="-1"' in html:
                issues.append({"severity": "moderate", "description": "Elements removed from tab order with tabindex=-1", "wcag": "2.4.3"})
    except:
        pass
    return issues

def check_images_alt() -> List[Dict]:
    issues = []
    try:
        req = urllib.request.Request(FRONTEND_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            html = resp.read().decode()
            img_count = html.count('<img ')
            if img_count > 0:
                alt_count = html.count('alt=')
                if alt_count < img_count:
                    issues.append({"severity": "critical", "description": f"Found {img_count} images, only {alt_count} have alt text", "wcag": "1.1.1"})
    except:
        pass
    return issues

def generate_report(issues: List[Dict]) -> str:
    report = ["# Accessibility Audit Report", "", "## Automated Checks", ""]
    
    if not issues:
        report.append("All automated checks passed.")
    else:
        by_severity = {}
        for issue in issues:
            sev = issue["severity"]
            by_severity.setdefault(sev, []).append(issue)
        
        for sev in ["critical", "serious", "moderate", "minor", "notice"]:
            if sev in by_severity:
                report.append(f"### {sev.upper()} ({len(by_severity[sev])})")
                for issue in by_severity[sev]:
                    report.append(f"- [{issue['wcag']}] {issue['description']}")
                report.append("")
    
    report.extend([
        "## Manual Testing Required",
        "",
        "- [ ] Keyboard navigation: Tab through all interactive elements",
        "- [ ] Screen reader: Navigate with NVDA/VoiceOver",
        "- [ ] Color contrast: Verify all text meets 4.5:1 ratio",
        "- [ ] Focus indicators: All focusable elements show visible focus",
        "- [ ] Error messages: Form errors announced to screen readers",
        "- [ ] Motion: Animations respect prefers-reduced-motion",
        "- [ ] Zoom: Interface works at 200% zoom level",
        "- [ ] Touch targets: Minimum 44x44px for interactive elements",
    ])
    
    return "\n".join(report)

if __name__ == "__main__":
    print("Running accessibility audit...")
    all_issues = []
    all_issues.extend(check_meta_tags())
    all_issues.extend(check_color_contrast_static())
    all_issues.extend(check_keyboard_navigation())
    all_issues.extend(check_images_alt())
    
    report = generate_report(all_issues)
    print(report)
    
    with open("a11y-report.md", "w") as f:
        f.write(report)
    
    critical = [i for i in all_issues if i["severity"] == "critical"]
    sys.exit(1 if critical else 0)

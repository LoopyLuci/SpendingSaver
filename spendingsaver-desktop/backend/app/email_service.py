import smtplib
import os
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from typing import Optional

SMTP_HOST = os.environ.get("SS_SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SS_SMTP_PORT", "587"))
SMTP_USER = os.environ.get("SS_SMTP_USER", "")
SMTP_PASS = os.environ.get("SS_SMTP_PASS", "")

def send_email(to: str, subject: str, body: str, html: Optional[str] = None) -> bool:
    if not SMTP_USER or not SMTP_PASS:
        return False
    try:
        msg = MIMEMultipart("alternative")
        msg["From"] = SMTP_USER
        msg["To"] = to
        msg["Subject"] = subject
        msg.attach(MIMEText(body, "plain"))
        if html:
            msg.attach(MIMEText(html, "html"))
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            server.starttls()
            server.login(SMTP_USER, SMTP_PASS)
            server.sendmail(SMTP_USER, to, msg.as_string())
        return True
    except Exception:
        return False

def format_report_html(month: int, year: int, income: float, expenses: float, by_category: dict) -> str:
    rows = ""
    for cat, data in by_category.items():
        rows += f"<tr><td>{cat}</td><td>${data['income']:.2f}</td><td>${data['expenses']:.2f}</td></tr>"
    return f"""
    <html><body>
    <h2>SpendingSaver Monthly Report - {month}/{year}</h2>
    <p><strong>Income:</strong> ${income:.2f}</p>
    <p><strong>Expenses:</strong> ${expenses:.2f}</p>
    <p><strong>Net:</strong> ${income - expenses:.2f}</p>
    <table border="1" cellpadding="8"><tr><th>Category</th><th>Income</th><th>Expenses</th></tr>{rows}</table>
    </body></html>
    """

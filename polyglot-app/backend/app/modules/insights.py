"""Insights module — registered through the ModuleRegistry.

Demonstrates the drop-in module pattern: this router's routes are attached to
the app automatically, no app/api.py changes needed for new endpoints here.
"""
from fastapi import APIRouter, Depends

from app.database import SessionLocal, TransactionModel
from app.deps import get_current_user

router = APIRouter()


@router.get("/api/insights/spending")
def spending_insights(months: int = 6, user=Depends(get_current_user)):
    db = SessionLocal()
    try:
        txs = db.query(TransactionModel).filter(TransactionModel.amount < 0, TransactionModel.deleted_at.is_(None)).order_by(TransactionModel.date.asc()).all()
        if not txs:
            return {"trends": [], "anomalies": [], "recommendations": []}
        monthly = {}
        for t in txs:
            month_key = t.date[:7]
            monthly.setdefault(month_key, 0.0)
            monthly[month_key] += abs(t.amount)
        sorted_months = sorted(monthly.keys())
        values = list(monthly.values())
        avg = sum(values) / len(values) if values else 0
        trend = "stable"
        if len(values) >= 2:
            recent = values[-1]
            previous = values[-2]
            if recent > previous * 1.1:
                trend = "increasing"
            elif recent < previous * 0.9:
                trend = "decreasing"
        anomalies = []
        for m, v in monthly.items():
            if v > avg * 1.5:
                anomalies.append({"month": m, "amount": v, "reason": "higher than average"})
        recommendations = []
        if trend == "increasing":
            recommendations.append("Spending is trending upward. Consider reviewing discretionary categories.")
        if anomalies:
            recommendations.append(f"An unusual spike detected in {anomalies[0]['month']}. Review transactions for that period.")
        return {"trends": [{"month": m, "amount": v} for m, v in monthly.items()], "average": avg, "trend": trend, "anomalies": anomalies, "recommendations": recommendations}
    finally:
        db.close()

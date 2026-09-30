"""Shared FastAPI dependencies, extracted so modules can reuse them without
circular-importing app.api."""
from fastapi import HTTPException, Header
from app.database import SessionLocal, UserModel
from app.auth import decode_token


def get_current_user(authorization: str = Header(None)):
    if not authorization:
        raise HTTPException(status_code=401, detail="missing authorization header")
    token = authorization.replace("Bearer ", "")
    user_id = decode_token(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="invalid token")
    db = SessionLocal()
    try:
        user = db.get(UserModel, user_id)
        if not user or not user.is_active:
            raise HTTPException(status_code=401, detail="user not found or inactive")
        return user
    finally:
        db.close()

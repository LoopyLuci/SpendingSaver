import uvicorn
from app.api import app

def main() -> None:
    uvicorn.run("app.api:app", host="127.0.0.1", port=8125, reload=False)

if __name__ == "__main__":
    main()

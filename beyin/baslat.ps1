Set-Location $PSScriptRoot
if (-not (Test-Path .venv)) {
    python -m venv .venv
    .\.venv\Scripts\pip install -r requirements.txt
}
.\.venv\Scripts\python -m uvicorn app:app --host 127.0.0.1 --port 8765

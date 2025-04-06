FROM python:3.9

WORKDIR /app

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc python3-dev && \
    rm -rf /var/lib/apt/lists/*

# Copy requirements first for caching
COPY crm-registration-api/requirements.txt .

# Install Python dependencies globally (no virtualenv)
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt && \
    pip install requests uvicorn fastapi  # Explicit install as fallback

# Copy application code
COPY crm-registration-api/ .

# Verify installations
RUN python -c "import requests; print(f'Requests version: {requests.__version__}')" && \
    python -c "import uvicorn; print(f'Uvicorn version: {uvicorn.__version__}')" && \
    python -c "import fastapi; print(f'FastAPI version: {fastapi.__version__}')"

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
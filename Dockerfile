# Stage 1: Build stage
FROM python:3.9 as python-build

WORKDIR /app

# Copy requirements first for better caching
COPY crm-registration-api/requirements.txt .

# Create virtual environment and install dependencies
RUN python -m venv /app/venv
RUN /app/venv/bin/pip install --no-cache-dir --upgrade pip && \
    /app/venv/bin/pip install --no-cache-dir -r requirements.txt

# Stage 2: Runtime stage
FROM python:3.9-slim

WORKDIR /app

# Copy only the virtual environment from the build stage
COPY --from=python-build /app/venv /app/venv

# Copy application code
COPY crm-registration-api/ .

# Ensure Python uses our virtual environment
ENV PATH="/app/venv/bin:$PATH"

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
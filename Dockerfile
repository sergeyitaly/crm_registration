# Dockerfile

FROM python:3.9

# Set working directory
WORKDIR /app

# Copy only requirements first for better caching
COPY crm-registration-api/requirements.txt .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application
COPY crm-registration-api/ .

# Expose port
EXPOSE 8000

# Run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

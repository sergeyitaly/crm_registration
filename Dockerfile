# Dockerfile
FROM python:3.11

# Set working directory
WORKDIR /app

# Copy the entire API directory
COPY crm-registration-api/ .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# For debugging (optional)
RUN ls -al

# Expose port
EXPOSE 8000

# Run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
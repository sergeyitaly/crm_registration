# Dockerfile
FROM python:3.9
WORKDIR /app
COPY crm-registration-api /app/crm-registration-api
COPY --from=crm-registration-api /app/crm-registration-api /app/crm-registration-api
RUN pip install --no-cache-dir -r /app/crm-registration-api/requirements.txt
RUN ls -al
EXPOSE 8000
CMD ["uvicorn", "crm-registration-api.main:app", "--host", "0.0.0.0", "--port", "8000"]
FROM python:3.13-alpine

WORKDIR /app
COPY service.py .

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=2s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/health || exit 1

CMD ["python", "service.py"]

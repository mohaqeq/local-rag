# Local RAG - PDF Question Answering System

A local Retrieval-Augmented Generation (RAG) application built with Kotlin and Ktor that allows you to upload 
PDF documents and query them using local LLMs via Ollama. Document embeddings are stored in PostgreSQL with pgVector 
for fast similarity search.

## Features

- PDF document ingestion and processing
- Vector similarity search using PostgreSQL pgVector
- Local LLM inference with Ollama
- Fast embedding generation with mxbai-embed-large
- Query expansion for better retrieval
- Fully local deployment - no external API calls

## Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Gradle (or use included wrapper)

## Quick Start

### Option 1: Run with Local Ollama (Recommended)

This option runs Ollama in Docker alongside the database.

```bash
# 1. Start all services including Ollama
docker-compose --profile ollama up -d

# 2. Wait for Ollama to download models (first time only)
docker logs -f local-rag-ollama-setup

# 3. Build and run the application
./gradlew run
```

### Option 2: Run with External Ollama

If you have Ollama running elsewhere (e.g., locally installed or remote server):

```bash
# 1. Start only the database
docker-compose up -d

# 2. Update src/main/resources/application.yaml
# Change ollamaBaseUrl to your Ollama instance URL

# 3. Build and run the application
./gradlew run
```

The application will be available at `http://localhost:8080`

## API Endpoints

### 1. Upload and Embed PDF Document

Upload a PDF file to be processed and stored in the vector database.

**Endpoint:** `POST /embed`

**Example using curl:**

```bash
curl -X POST http://localhost:8080/embed \
  -F "file=@/path/to/your/document.pdf"
```

**Success Response (200 OK):**
```json
{
  "message": "File embedded successfully"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "No valid PDF file provided"
}
```

**What happens behind the scenes:**
1. PDF text is extracted using Apache PDFBox
2. Text is split into chunks (7500 characters with 100 characters overlap)
3. Each chunk is embedded using Ollama's mxbai-embed-large model (1024 dimensions)
4. Document chunks and embeddings are stored in PostgreSQL with metadata
5. IVFFlat index enables fast similarity search

### 2. Query the Knowledge Base

Ask questions about your uploaded documents.

**Endpoint:** `POST /query`

**Example using curl:**

```bash
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What are the main findings in the document?"
  }'
```

**Success Response (200 OK):**
```json
{
  "message": "Based on the document, the main findings include... [AI-generated answer based on retrieved context]"
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "Query cannot be empty"
}
```
_Retry the request if you received timeout. It takes time to load the model for in memory for the first time_
**What happens behind the scenes:**
1. Query is expanded into 5 variations using the LLM for better retrieval
2. Each variation is embedded using mxbai-embed-large
3. Top 3 similar document chunks are retrieved for each variation using cosine similarity
4. Retrieved chunks are deduplicated and combined into context
5. Context is sent to Ollama's qwen3 LLM to generate an answer
6. Answer is returned to the client

## API Documentation

Interactive API documentation is available via Swagger UI:

```
http://localhost:8080/swagger
```

OpenAPI specification:

```
http://localhost:8080/openapi
```

## Configuration

Edit `src/main/resources/application.yaml` to customize.

## Docker Services

### PostgreSQL with pgVector

```bash
# Start only the database
docker-compose up db -d

### Ollama (Optional)

```bash
# Start Ollama with the ollama profile
docker-compose --profile ollama up ollama -d

# View available models
docker exec local-rag-ollama ollama list

# Pull additional models
docker exec local-rag-ollama ollama pull llama3.2

# Test Ollama directly
curl http://localhost:11434/api/tags
```

## Building and Testing

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application
./gradlew run

# Build executable JAR
./gradlew shadowJar
```

## Troubleshooting

### Application can't connect to Ollama

**Error:** `Connection refused to http://localhost:11434`

**Solution:**
- If using Docker Ollama: Ensure the service is running with `docker ps | grep ollama`
- If using external Ollama: Update `ollamaBaseUrl` in `application.yaml`
- Check Ollama is responding: `curl http://localhost:11434/api/tags`

### Database connection error

**Error:** `Connection to localhost:5432 refused`

**Solution:**
```bash
# Start PostgreSQL
docker-compose up db -d

# Check it's running
docker ps | grep postgres

# Check logs
docker logs postgres-pgvector
```

### Request timeout on /query

**Solution:** The application is configured with 5-minute timeouts. If you still experience timeouts:
- First query takes longer as the model loads into memory
- Consider using a smaller/faster model (e.g., `llama3.2:1b` instead of `qwen3`)
- Check Ollama logs: `docker logs local-rag-ollama`

### Empty responses from /query

**Solution:**
- Ensure you've uploaded at least one PDF document via `/embed` first
- Check that embeddings were generated successfully in the logs
- Verify documents are in the database:
  ```bash
  docker exec -it postgres-pgvector psql -U dev_user -d embedding_db \
    -c "SELECT COUNT(*) FROM documents;"
  ```

## Performance Considerations

- **Vector Index:** IVFFlat index trades some accuracy for speed (~100 lists)
- **Connection Pool:** 2-10 concurrent database connections
- **Chunk Size:** 7500 characters balances context vs. precision
- **Timeout:** 5-minute timeout accommodates slower LLM inference
- **Embedding Model:** mxbai-embed-large provides good accuracy at 1024 dimensions

## License

**MIT**

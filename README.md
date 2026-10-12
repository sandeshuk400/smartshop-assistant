# SmartShop Assistant

An AI-powered support assistant on top of a Spring Boot microservices system.
A user can chat with it to:

- Ask policy questions ("What's your return policy?") -> answered from documents via RAG
- Check live data ("Where is my order #1234?") -> the model calls the order service
- Take actions ("Cancel my last order") -> with proper authorization checks

## Services

| Service | Port | Responsibility |
|---|---|---|
| order-service | 8081 | Create/get/cancel orders |
| customer-service | 8082 | Get customer profile |
| inventory-service | 8083 | Check stock by SKU |
| assistant-service | 8080 | Chat API, RAG, tool calling, memory |

## Run (Phase 0)

1. Start Postgres: `docker compose up -d`
2. Run order-service: `cd order-service && mvn spring-boot:run`
3. Create an order:
   `curl -X POST http://localhost:8081/orders -H "Content-Type: application/json" -d '{"customerId": 1, "totalAmount": 499.99}'`
4. Fetch it: `curl http://localhost:8081/orders/1`

## Roadmap

- Phase 1: Spring AI chat endpoint with streaming
- Phase 2: RAG over policy documents using pgvector
- Phase 3: Tool calling into services, JWT pass-through, Redis chat memory
- Phase 4: Resilience4j, rate limiting, caching, cost tracking, tracing
- Phase 5: Eval suite in CI, MCP server, Docker deployment
- Phase 6: Final README, architecture diagram, demo video
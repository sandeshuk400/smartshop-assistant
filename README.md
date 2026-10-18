# SmartShop Assistant

A small microservices project for an online grocery shop, with an AI support chat that answers customer questions from the shop's FAQ and can look up order information.

> **Demo data:** the address, phone number and email in this repository are placeholders.

## What it does

- **Support chat:** customers ask about shipping, returns, payments, the shop location or their order, and get an answer in a simple web chat.
- **FAQ answers:** `faq.txt` is loaded into a vector store on startup, so the assistant answers from the shop's own policies instead of guessing.
- **Order lookups:** the assistant reads order data through a separate database connection (`OrderRepository`).
- **Contact panel:** a "Contact us" button in the chat shows call, WhatsApp and email links.

## Services

| Service | Purpose |
|---|---|
| `assistant-service` | Chat API, FAQ ingestion, and the chat web page in `static/index.html` |
| `customer-service` | Customer data |
| `inventory-service` | Product stock |
| `order-service` | Creating and storing orders |

PostgreSQL runs in Docker, defined in `docker-compose.yml`.

## Tech stack

- Java and Spring Boot (Maven)
- PostgreSQL in Docker, with a `vector_store` table for FAQ embeddings
- Plain HTML, CSS and JavaScript for the chat page (light and dark mode)

## Prerequisites

- Java JDK and Maven
- Docker Desktop
- An API key for the AI model provider used by `assistant-service` (see `assistant-service/src/main/resources/application.yml`)

## Getting started

1. Clone the repository:

```
   git clone https://github.com/sandeshuk400/smartshop-assistant.git
   cd smartshop-assistant
```

2. Create your `.env` file and set a real password:

```
   copy .env.example .env
```

   Open `.env` and replace the placeholder after `DB_PASSWORD=` with a long random password. `.env` is ignored by git and must never be committed.

3. Start the databases:

```
   docker compose up -d
```

4. Start each service in its own terminal:

```
   cd order-service
   mvn spring-boot:run
```

   Repeat for `customer-service`, `inventory-service` and `assistant-service`.

5. Open the chat at the address `assistant-service` prints on startup (usually `http://localhost:8080`).

## Try it

- Click a suggested question such as "Where is your shop?" or "What is your return policy?".
- Ask about shipping charges or cash on delivery.
- On Windows, run `new-order.bat` to create a sample order, then ask the assistant about it.

## Changing the FAQ

The shop's answers live in `assistant-service/src/main/resources/faq.txt`. After editing it, clear the stored copy so it reloads on the next start:

```
docker exec smartshop-db psql -U smartshop -d smartshop -c "TRUNCATE vector_store;"
```

Then restart `assistant-service`.

## Configuration

| Variable | Where | Description |
|---|---|---|
| `DB_PASSWORD` | `.env` | PostgreSQL password, read by `docker-compose.yml` and every service |

Real credentials stay in `.env` only. Committed files contain `${DB_PASSWORD}` placeholders.

## Project structure

```
smartshop-assistant/
├── assistant-service/    chat API, FAQ ingestion, chat page
├── customer-service/
├── inventory-service/
├── order-service/
├── docker-compose.yml
├── .env.example
└── new-order.bat         sample order helper (Windows)
```

## Author

Built by SandeshUK400 as a portfolio project.
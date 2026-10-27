# PB-RAG backend

PostgreSQL and Redis addresses live in `src/main/resources/application.yml`. Change those values to the server addresses before starting. The application does not install or start either service.

```bash
./mvnw spring-boot:run
```

Health check: `http://localhost:8080/actuator/health`

OpenAI and PgVector beans stay off until a key is available:

```bash
OPENAI_API_KEY=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai
```

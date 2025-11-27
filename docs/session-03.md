# Session 03: Chat Memory, Persistence, and Data Integration

This session covers two foundational patterns for AI-powered applications:

1. **Part 1: Chat Memory** - Persistent conversation memory for multi-turn interactions
2. **Part 2: ETL Pipeline** - Document loading and processing for knowledge bases

Together, these patterns prepare VoyagerMate for RAG (Retrieval Augmented Generation) in Session 4.

---

# Part 1: Chat Memory and Conversation Persistence

This part covers implementing persistent conversation memory in Spring AI applications. VoyagerMate gains the ability
to maintain context across multiple interactions within a session, enabling more natural multi-turn conversations.

---

## 1. Why Chat Memory Matters

Large Language Models are inherently stateless. Each API call is independent, with no built-in awareness of previous
interactions. This creates challenges for conversational applications where context is essential.

### The Problem

```
User: "I want to visit Japan for 2 weeks"
AI: "Great! Japan offers amazing experiences..."

User: "What about food recommendations?"
AI: "I'd be happy to help with food recommendations. What cuisine or destination are you interested in?"
```

Without memory, the AI loses the Japan context and asks redundant questions.

### The Solution

Chat memory maintains conversation history, allowing the AI to build on previous exchanges:

```
User: "I want to visit Japan for 2 weeks"
AI: "Great! Japan offers amazing experiences..."

User: "What about food recommendations?"
AI: "For your 2-week Japan trip, here are some must-try dishes..."
```

The AI remembers the destination and trip duration, providing contextually relevant responses.

---

## 2. Spring AI Memory Architecture

Spring AI provides a layered architecture for managing conversation memory:

```
┌─────────────────────────────────────────────────────────┐
│                    ChatClient                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │           MessageChatMemoryAdvisor                │  │
│  │  (Intercepts requests, injects history)           │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              MessageWindowChatMemory                    │
│  (Sliding window of N most recent messages)             │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│             ChatMemoryRepository                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  InMemory   │  │    JDBC     │  │   Cassandra     │  │
│  │ (default)   │  │ (PostgreSQL)│  │    Neo4j        │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Core Components

| Component | Purpose |
|-----------|---------|
| `ChatMemory` | Interface for storing and retrieving messages by conversation ID |
| `MessageWindowChatMemory` | Implementation that maintains a sliding window of recent messages |
| `ChatMemoryRepository` | Storage backend interface (in-memory, JDBC, Cassandra, Neo4j) |
| `MessageChatMemoryAdvisor` | Advisor that automatically injects history into prompts |

---

## 3. VoyagerMate Implementation

### 3.1 Infrastructure Setup

VoyagerMate uses PostgreSQL for persistent storage with Flyway for schema management.

**Docker Compose Configuration** (`docker-compose.yml`):

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: voyagermate-postgres
    environment:
      POSTGRES_DB: voyagermate
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5435:5432"
    volumes:
      - voyagermate_pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d voyagermate"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  voyagermate_pgdata:
```

**Flyway Migration** (`V1__create_chat_memory_table.sql`):

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    type            VARCHAR(50) NOT NULL,
    "timestamp"     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, "timestamp")
);

CREATE INDEX idx_chat_memory_conversation 
    ON SPRING_AI_CHAT_MEMORY(conversation_id);
```

### 3.2 Configuration

**ChatMemoryConfig.java**:

```java
@Configuration
public class ChatMemoryConfig {

    @Value("${voyagermate.chat.memory.window-size:20}")
    private int windowSize;

    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(windowSize)
                .build();
    }
}
```

**ChatClientConfig.java** (conversational client):

```java
@Bean
@Qualifier("conversationalChatClient")
public ChatClient conversationalChatClient(
        ChatClient.Builder builder,
        ChatMemory chatMemory,
        @Value("${spring.ai.azure.openai.chat.options.deployment-name}") String deployment) {
    return builder
            .defaultSystem("You are VoyagerMate, an upbeat travel planning copilot. "
                    + "Reference previous messages in this conversation when relevant.")
            .defaultOptions(AzureOpenAiChatOptions.builder()
                    .deploymentName(deployment)
                    .build())
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
}
```

### 3.3 ConversationService

The service layer provides methods for session-based chat operations:

```java
@Service
public class ConversationService {

    private final ChatClient conversationalChatClient;
    private final ChatMemory chatMemory;

    public ChatResponsePayload chat(String sessionId, String prompt) {
        var response = conversationalChatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .user(prompt)
                .call();
        // Extract and return response...
    }

    public ConversationHistory getHistory(String sessionId) {
        List<Message> messages = chatMemory.get(sessionId, Integer.MAX_VALUE);
        // Convert to ConversationHistory...
    }

    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }

    public List<String> listSessions() {
        // Query database for distinct conversation IDs...
    }
}
```

---

## 4. Shell Commands

VoyagerMate exposes four new commands for managing conversational sessions:

### chat-session

Chat within a named session, maintaining conversation history:

```shell
shell:>chat-session --session japan-2025 --prompt "Plan a 2-week trip to Japan"

Session: japan-2025
Model: gpt-4o
Latency: 1234 ms

I'd be happy to help you plan a memorable 2-week trip to Japan! Let me suggest...
```

### show-history

Display the full conversation history for a session:

```shell
shell:>show-history --session japan-2025

Session: japan-2025 (4 messages)
────────────────────────────────────
[USER]
Plan a 2-week trip to Japan

[ASSISTANT]
I'd be happy to help you plan...

[USER]
Focus on Kyoto and traditional culture

[ASSISTANT]
For a culturally immersive experience in Kyoto...
```

### list-sessions

Show all active conversation sessions:

```shell
shell:>list-sessions

Active Sessions (3):
  - japan-2025 (4 messages)
  - europe-summer (12 messages)
  - quick-trip (2 messages)
```

### clear-history

Remove all messages from a session:

```shell
shell:>clear-history --session japan-2025

Cleared 4 messages from session: japan-2025
```

---

## 5. Memory Window Sizing

The `MessageWindowChatMemory` maintains a sliding window of the N most recent messages. Choosing the right window size
involves balancing context quality against token costs.

### Considerations

| Factor | Smaller Window (5-10) | Larger Window (20-50) |
|--------|----------------------|----------------------|
| Token cost | Lower | Higher |
| Context depth | Recent only | Extended history |
| Response latency | Faster | Slower |
| Use case | Quick queries | Complex planning |

### Configuration

```yaml
voyagermate:
  chat:
    memory:
      window-size: ${CHAT_MEMORY_WINDOW_SIZE:20}
```

For VoyagerMate's travel planning use case, 20 messages provides sufficient context for multi-turn itinerary
discussions while keeping costs manageable.

---

## 6. Two ChatClient Strategy

VoyagerMate maintains two `ChatClient` instances:

1. **Primary (stateless)**: For single-turn commands like `chat`, `describe-image`, `plan-itinerary`
2. **Conversational**: For multi-turn sessions via `chat-session`

This separation ensures:

- Backward compatibility with existing commands
- Clear distinction between stateless and stateful interactions
- Independent configuration (e.g., different system prompts)

```java
@Bean
@Primary
public ChatClient chatClient(ChatClient.Builder builder, ...) {
    // Stateless client for single-turn commands
}

@Bean
@Qualifier("conversationalChatClient")
public ChatClient conversationalChatClient(ChatClient.Builder builder, ChatMemory chatMemory, ...) {
    // Memory-enabled client for sessions
}
```

---

## 7. Database Schema Details

The `SPRING_AI_CHAT_MEMORY` table stores conversation messages:

| Column | Type | Description |
|--------|------|-------------|
| `conversation_id` | VARCHAR(255) | Session identifier (e.g., "japan-2025") |
| `content` | TEXT | Message content |
| `type` | VARCHAR(50) | Message type: USER, ASSISTANT, SYSTEM |
| `timestamp` | TIMESTAMP | When the message was stored |

Primary key: `(conversation_id, timestamp)`

Indexes optimize common queries:
- `idx_chat_memory_conversation`: Fast lookup by session ID
- `idx_chat_memory_timestamp`: Efficient ordering within sessions

---

## 8. Testing Strategies

### Unit Testing ConversationService

```java
@Test
void shouldMaintainConversationContext() {
    // Given
    String sessionId = "test-session";
    
    // When - First message
    service.chat(sessionId, "Plan a trip to Paris");
    
    // When - Follow-up referencing context
    var response = service.chat(sessionId, "What about restaurants?");
    
    // Then - Response should reference Paris
    assertThat(response.reply()).containsIgnoringCase("Paris");
}

@Test
void shouldClearHistoryCompletely() {
    // Given
    String sessionId = "test-session";
    service.chat(sessionId, "Hello");
    
    // When
    service.clearHistory(sessionId);
    
    // Then
    var history = service.getHistory(sessionId);
    assertThat(history.isEmpty()).isTrue();
}
```

### Integration Testing with TestContainers

For full integration tests, use TestContainers to spin up a PostgreSQL instance:

```java
@Testcontainers
@SpringBootTest
class ConversationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    // Tests...
}
```

---

## 9. Advanced Patterns

### 9.1 Session Expiration

For production systems, consider implementing session expiration:

```sql
-- Add expiration column
ALTER TABLE SPRING_AI_CHAT_MEMORY 
ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

-- Clean up expired sessions
DELETE FROM SPRING_AI_CHAT_MEMORY 
WHERE expires_at < CURRENT_TIMESTAMP;
```

### 9.2 Context Compaction

For long-running conversations, implement summarization to compress older messages:

```java
public void compactHistory(String sessionId) {
    List<Message> messages = chatMemory.get(sessionId, Integer.MAX_VALUE);
    if (messages.size() > COMPACTION_THRESHOLD) {
        // Summarize older messages
        String summary = summarizeMessages(messages.subList(0, messages.size() - RECENT_COUNT));
        
        // Replace with summary + recent messages
        chatMemory.clear(sessionId);
        chatMemory.add(sessionId, List.of(new SystemMessage("Previous conversation summary: " + summary)));
        chatMemory.add(sessionId, messages.subList(messages.size() - RECENT_COUNT, messages.size()));
    }
}
```

### 9.3 Multi-User Sessions

For applications with authentication, incorporate user identity into session IDs:

```java
public String chat(String userId, String sessionId, String prompt) {
    String qualifiedSessionId = userId + ":" + sessionId;
    return conversationalChatClient.prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, qualifiedSessionId))
            .user(prompt)
            .call()
            .content();
}
```

---

## 10. Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "No conversation history found" | Wrong session ID or cleared history | Check session ID spelling |
| "Connection refused" | PostgreSQL not running | Run `docker-compose up -d` |
| Memory not persisting | Schema not created | Check Flyway migrations ran |
| Slow responses | Large window size | Reduce `voyagermate.chat.memory.window-size` |

### Debugging

Enable debug logging for memory operations:

```yaml
logging:
  level:
    org.springframework.ai.chat.memory: DEBUG
```

---

## 11. Part 1 Summary

Part 1 introduced:

1. **Chat Memory Concepts**: Why stateless LLMs need external memory for multi-turn conversations
2. **Spring AI Architecture**: ChatMemory, MessageWindowChatMemory, and ChatMemoryRepository
3. **JDBC Persistence**: PostgreSQL storage with Flyway migrations
4. **VoyagerMate Integration**: ConversationService, shell commands, and two-client strategy
5. **Advanced Patterns**: Window sizing, context compaction, and session management

---

# Part 2: ETL Pipeline for Data Integration

This part covers Spring AI's ETL (Extract, Transform, Load) pipeline for loading and processing travel documents.
These documents will serve as the knowledge base for RAG in Session 4.

---

## 12. ETL Pipeline Architecture

Spring AI provides a modular ETL pipeline for processing documents:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  DocumentReader │ -> │   Transformer   │ -> │  DocumentWriter │
│  (Extract)      │    │   (Transform)   │    │    (Load)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
     TextReader           TokenTextSplitter      VectorStore
     JsonReader           KeywordEnricher        (Session 4)
     PdfDocumentReader    SummaryEnricher
     TikaDocumentReader
```

### Core Components

| Component | Purpose |
|-----------|---------|
| `DocumentReader` | Extracts content from files (PDF, JSON, text, etc.) |
| `Transformer` | Modifies documents (chunking, enriching metadata) |
| `DocumentWriter` | Persists documents (VectorStore in Session 4) |
| `Document` | Container with content text and metadata map |

---

## 13. Document Readers

### TextReader

Reads plain text and markdown files:

```java
Resource resource = new ClassPathResource("data/travel-guides/tokyo-guide.md");
TextReader reader = new TextReader(resource);
reader.getCustomMetadata().put("source_type", "markdown");
reader.getCustomMetadata().put("destination", "Tokyo");
List<Document> documents = reader.get();
```

### JsonReader

Processes structured JSON data:

```java
Resource resource = new ClassPathResource("data/destinations.json");
JsonReader reader = new JsonReader(resource, "destinations");
List<Document> documents = reader.get();
```

### PdfDocumentReader

Extracts text from PDF files:

```java
Resource resource = new ClassPathResource("data/travel-tips.pdf");
PdfDocumentReader reader = new PdfDocumentReader(resource);
List<Document> documents = reader.get();
```

---

## 14. Document Transformers

### TokenTextSplitter

Chunks large documents into smaller pieces for better retrieval:

```java
TokenTextSplitter splitter = new TokenTextSplitter(
    800,    // defaultChunkSize - target size for each chunk
    100,    // minChunkSizeChars - minimum size
    5,      // minChunkLengthToEmbed - minimum length
    10000,  // maxNumChunks - safety limit
    true    // keepSeparator - preserve paragraph breaks
);

List<Document> chunks = splitter.apply(documents);
```

### Why Chunking Matters

| Chunk Size | Pros | Cons |
|------------|------|------|
| Small (200-400) | Precise retrieval | Loses context |
| Medium (600-1000) | Good balance | Standard choice |
| Large (1500+) | Full context | May include noise |

---

## 15. VoyagerMate Implementation

### TravelDocumentService

The service loads and processes travel knowledge:

```java
@Service
public class TravelDocumentService {

    private final TokenTextSplitter textSplitter;
    private final List<Document> loadedDocuments = new CopyOnWriteArrayList<>();

    public int loadTravelGuides() {
        Resource[] resources = resourceResolver
            .getResources("classpath:data/travel-guides/*.md");
        
        List<Document> guides = new ArrayList<>();
        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            reader.getCustomMetadata().put("source_type", "markdown");
            guides.addAll(reader.get());
        }
        
        // Apply chunking
        List<Document> chunks = textSplitter.apply(guides);
        loadedDocuments.addAll(chunks);
        return chunks.size();
    }

    public List<Document> searchDocuments(String query) {
        // Simple keyword search (semantic search in Session 4)
        return loadedDocuments.stream()
            .filter(doc -> doc.getText().toLowerCase()
                .contains(query.toLowerCase()))
            .toList();
    }
}
```

### Sample Travel Data

VoyagerMate includes sample travel guides in `src/main/resources/data/`:

```
data/
├── travel-guides/
│   ├── tokyo-guide.md      # Tokyo travel information
│   └── lisbon-guide.md     # Lisbon travel information
└── destinations.json        # Structured destination data
```

---

## 16. ETL Shell Commands

### load-guides

Load and process all travel documents:

```shell
shell:>load-guides

Documents loaded successfully!
  Travel guides: 12 chunks
  Destination data: 8 documents
  Total documents: 20

Use 'show-documents' to view loaded documents or 'search-docs' to search.
```

### show-documents

Display loaded documents with metadata:

```shell
shell:>show-documents --limit 3

Loaded Documents (20 total)
────────────────────────────────────────────────────────────

[Document 1]
  Source: markdown
  File: tokyo-guide.md
  Content: # Tokyo Travel Guide ## Overview Tokyo, Japan's...

[Document 2]
  Source: markdown
  File: lisbon-guide.md
  Content: # Lisbon Travel Guide ## Overview Lisbon, Portug...

[Document 3]
  Source: json
  Destination: Tokyo
  Content: Destination: Tokyo, Japan Description: Japan's b...
```

### search-docs

Search documents by keyword:

```shell
shell:>search-docs --query "temples"

Found 3 document(s) matching 'temples'
────────────────────────────────────────────────────────────

[Result 1]
  Source: markdown | tokyo-guide.md
  ...Senso-ji Temple Tokyo's oldest Buddhist temple...

[Result 2]
  Source: json | Tokyo
  ...Highlights: Senso-ji Temple, Shibuya Crossing...

Note: This is keyword search. Use RAG (Session 4) for semantic search.
```

### clear-docs

Clear loaded documents from memory:

```shell
shell:>clear-docs

Cleared 20 documents from memory.
```

---

## 17. Document Model

Each `Document` contains:

```java
public class Document {
    private String id;           // Unique identifier
    private String text;         // Content text
    private Map<String, Object> metadata;  // Key-value metadata
}
```

### Metadata Best Practices

Include metadata that aids retrieval and filtering:

```java
Map<String, Object> metadata = new HashMap<>();
metadata.put("source_type", "markdown");       // Document type
metadata.put("file_name", "tokyo-guide.md");   // Origin file
metadata.put("destination", "Tokyo");          // Domain-specific
metadata.put("category", "travel_guide");      // Classification
metadata.put("chunk_index", 3);                // Position in source
```

---

## 18. Preparing for Session 4 RAG

Documents processed in Session 3 are ready for RAG in Session 4:

```
Session 3: ETL Pipeline          Session 4: RAG
┌─────────────────────┐         ┌─────────────────────┐
│   DocumentReader    │         │   EmbeddingModel    │
│   (Load files)      │         │   (Generate vectors)│
└─────────┬───────────┘         └─────────┬───────────┘
          │                               │
          ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│   TokenTextSplitter │         │   PgVectorStore     │
│   (Chunk documents) │         │   (Store embeddings)│
└─────────┬───────────┘         └─────────┬───────────┘
          │                               │
          ▼                               ▼
┌─────────────────────┐         ┌─────────────────────┐
│   List<Document>    │ ------> │ QuestionAnswerAdvis │
│   (In memory)       │         │ (Semantic retrieval)│
└─────────────────────┘         └─────────────────────┘
```

---

## 19. Session 3 Complete Summary

This session covered two foundational patterns:

### Part 1: Chat Memory
- Persistent conversation history with PostgreSQL
- `MessageChatMemoryAdvisor` for automatic context injection
- Session-based chat commands (`chat-session`, `show-history`, etc.)

### Part 2: ETL Pipeline
- Document loading with `TextReader`, `JsonReader`, `PdfDocumentReader`
- Document transformation with `TokenTextSplitter`
- Knowledge base management commands (`load-guides`, `search-docs`, etc.)

### Next: Session 4 RAG
- Embed documents using Azure OpenAI embeddings
- Store vectors in PGVector
- Semantic search with `QuestionAnswerAdvisor`
- Context-grounded responses from travel knowledge

---

## References

- [Spring AI Chat Memory Documentation](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI ETL Pipeline](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)


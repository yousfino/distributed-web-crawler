# IN PROGRESS ...
# Distributed Web Crawler & Analysis Platform
 
## Description
A system where users submit URLs or seed domains to crawl, extract structured metadata (headings, links, outbound status codes, keyword densities, and reading levels), and view interactive graph and table reports.

## Key Concepts
1. Concurrency & Multi-Threading
    - Worker Thread Pool
    - Producer-Consumer Architecture
    - Thread Safety & Race Conditions
2. Core OOP Principles & Abstract Classes
    - Abstract Base Classes
    - Polymorphism & Strategy Pattern
    - Factory Pattern
    - Observer Pattern / Event Bus
3. Data Structures & Algorithmic Complexities
    - URL Frontier (Deduplication & Traversal):
        - Breadth-First Search (BFS): Traverses pages level-by-level across internal links using a FIFO Queue.
        - Visited Set ($O(1)$ Average Lookup): Hash set using canonicalized URL hashes (SHA-256) to avoid memory bloat from storing raw long string URLs.
        - Bloom Filter (Space Complexity Optimization): Implement a bit array with k hash functions as a preliminary filter before hitting persistent storage, drastically reducing memory footprint for millions of discovered links.
    - Link Graph & Cycle Detection
    - Top-K Keyword / Entity Extraction:
        - Used a Min-Heap (Priority Queue) to track the top k recurring keywords across crawled documents in O(N log K) time rather than sorting entire frequency arrays in O(N log N) time.
    - Trie (Prefix Tree)

## Architectural Layout
### Layer: API Layer
Responsibility: Accepts crawl requests, queries reports, serves WebSocket metrics.
Key Best Practices: Dependency injection, strict request validation, structured error handling.

### Layer: Orchestration Layer
Responsibility: Manages crawl lifecycles, assigns thread pools, enforces timeouts.
Key Best Practices: Graceful shutdown (`SIGINT`/`SIGTERM` handling, draining queues).

### Layer: Worker Engine
Responsibility: HTTP execution, rate-limiting, retry policies with exponential backoff.
Key Best Practices: Idempotency, circuit breaker pattern for failing domains.

### Layer: Persistence Layer
Responsibility: Stores node metadata, raw text, and link relationships.
Key Best Practices: Database migrations, indexing foreign keys, connection pooling.

## Tech Stack
- Java 17
- Gradle (Kotlin DSL)
- Spring Boot 3.x


## Completion alerts and CSV reports

- Enable **Notify me when crawling ends** before or during a crawl. Keep the page open. If browser notification permission is granted, a desktop alert appears once when the job ends; an in-app message is also shown. If notifications are unavailable or blocked, the in-app message still works. Manually stopped jobs are labeled “stopped.”
- Choose **Export CSV** after at least one page is processed. During a crawl this downloads a snapshot; export again after completion for the final report. The report includes all stored pages for the current job, including pages outside the 50-entry live log. Starting a new crawl replaces the current job's results, so export before starting another job.
- Reports use one row per page, sorted by URL, with consistent columns for job ID, URL, title, HTTP status, content type, word count, response time in milliseconds, discovered link count, headings, the page's top 10 keywords with counts, and crawl time in UTC. Word counts follow the crawler's existing token rules (terms of at least three characters). Failed HTTP responses are included when processed; network requests that fail before a response are not stored as pages.
- CSV downloads use UTF-8 with a byte-order mark for spreadsheet compatibility, quoted fields, and escaped quotes/newlines. Text that could be interpreted as a spreadsheet formula is prefixed with an apostrophe. Filter by HTTP status to inspect errors, sort response times to find slow pages, and compare titles, headings, and keywords for content reporting.
- API consumers can download the same report using `GET /api/crawler/export` (`text/csv; charset=UTF-8`).

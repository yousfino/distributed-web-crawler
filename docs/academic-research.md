# Research Atlas

Research Atlas adds literature discovery and manual screening to the existing crawler. It is an initial single-user research workspace, not a complete systematic review platform. The Web Crawler tab retains the previous crawling, notification, and page-report features.

## Research workflow

1. Create a project with a name and research question.
2. Search OpenAlex and/or Crossref using keywords, publication years, and publication type. Searches retrieve up to 25 records per source. Search again with different terms to grow the collection; results are not exhaustive and there is no automatic pagination in this release.
3. Expand a paper to read its available abstract, follow source/open-access links, and inspect provenance. A missing open-access link means availability is unknown; it does not establish that the paper is paywalled. Crossref publisher links and licenses alone are not treated as proof of open access.
4. Mark papers Included, Excluded, or Undecided, add notes, and supply a reason for exclusions. Save each review before moving to another paper. Decisions are project-specific.
5. Export all collected papers or only Included papers as CSV or BibTeX. Exports use the selected export scope, independently of the on-screen text and decision filters.

Duplicates are matched by normalized DOI, then provider record ID, then a conservative combination of title, year, and first author when a DOI is missing. Different nonempty DOIs remain separate. Merging enriches missing metadata and appends provenance without overwriting screening decisions or notes. Fuzzy title matching, citation graphs, author-identity resolution, PDF extraction, and AI summaries are future work.

Each source occurrence stores its provider, source ID/URL, search ID, exact query, and retrieval time. Search history also retains year/type filters, chosen sources, source-record count, newly added count, and warnings. Source failures produce visible warnings while keeping available results and prior reviews. A successful zero-result search is distinct from a failed source.

## Start locally

Prerequisites: Java 17, Node compatible with Vite 8 (Node 24 is verified), and the repository's Gradle wrapper. From `backend`, run `bash gradlew bootRun`; from `frontend`, run `npm ci` then `npm run dev`. The development frontend proxies `/api` and `/ws` to the backend on port 8080. `API_PROXY_TARGET` can override that development target.

Research data defaults to a persistent H2 database at `backend/data/research` when the backend is started from its directory. Flyway applies schema migrations automatically. Stop the application before copying its H2 database files for a backup. Research data is ignored by Git; it must be backed up separately. Tests use a separate in-memory database and local scholarly API fixtures, not live scholarly services.

For this managed cloud workspace, use `GRADLE_USER_HOME=/workspace/.gradle` and the installed Java 17 toolchain; the existing local Gradle initialization redirects the repository's Windows-specific build path to `/workspace/.setup/backend-build`. Run the built application from `backend`:

```sh
/workspace/.setup/jdk-17.0.12/bin/java \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
  -jar /workspace/.setup/backend-build/libs/crawler-backend-0.0.1-SNAPSHOT.jar
```

The explicit trust store preserves the managed environment's CA trust for source requests. The scholarly HTTP client honors `HTTPS_PROXY` and bypasses it only for loopback fixture endpoints. It uses fixed provider endpoints, bounded requests, and timeouts; source URLs cannot be supplied by users through the API.

## PostgreSQL

Set these process variables before starting the backend to use PostgreSQL instead of H2:

| Variable | Purpose |
| --- | --- |
| `RESEARCH_DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://127.0.0.1:5432/research` |
| `RESEARCH_DATABASE_USER` | Database user, e.g. `research` |
| `RESEARCH_DATABASE_PASSWORD` | Database password; supply securely, never commit it |

An optional `compose.yaml` starts PostgreSQL 16 with persistent storage and a readiness check. Set `RESEARCH_DATABASE_PASSWORD` securely, then run `docker compose up -d postgres` from the repository root. Supply the same password to the backend. Avoid `docker compose down -v` unless intentionally deleting the database. Changing database URLs selects another database; it does not migrate existing H2 collections into PostgreSQL.

This release stores project documents in a SQL table with transactional row locking to preserve concurrent search/screening writes. It targets modest literature collections; normalized relational paper/citation tables and large-collection pagination can follow. There is no authentication or per-user access control yet; use the workspace locally or behind an appropriate authenticated deployment.

## Scholarly service configuration

Allow HTTPS access to `api.openalex.org` and `api.crossref.org` in the hosting environment. A saved network draft does not grant access until applied. API availability and provider requirements can change.

- `OPENALEX_API_KEY`: optional configured key, used when supplied. If OpenAlex returns an access error, configure an appropriate key in secure environment settings according to its current service requirements. Never enter keys in queries or source URLs.
- `RESEARCH_CONTACT_EMAIL`: optional contact address used for scholarly API identification and Crossref's polite pool.

No secret values are stored in project provenance or reports. Rate-limit/access failures are surfaced in search history; the UI does not fabricate substitute results. Live API validation was blocked by the managed environment's network policy during implementation. Adapter behavior was validated using recorded-shape local fixtures; enabling domains is still necessary for real searches.

## Reports

CSV uses one paper per row, sorted by title, with project identity/question, stable paper ID, DOI, title, authors, year, venue, publication type, abstract, record/open-access URLs, screening decision/reason/notes, and source/search provenance. Search filter strings include year bounds, type, and chosen sources. Multiple provenance values use newline-separated cells in matching order; enable Wrap Text in your spreadsheet. Timestamps are UTC. UTF-8 with a BOM, quoted fields, escaped quotes/newlines, and spreadsheet formula neutralization make the data suitable for reporting.

BibTeX uses stable paper-based citation keys, UTF-8 metadata, and escaped LaTeX characters. Article and book-chapter records use `article` and `incollection`; other types use `misc`. Missing metadata is omitted, not invented. Review the source records before citing.

## API

- `GET /api/research/projects`: project summaries.
- `POST /api/research/projects`: `{ "name": "…", "question": "…" }`.
- `GET /api/research/projects/{id}`: papers and search history.
- `POST /api/research/projects/{id}/searches`: `{ "query": "heat health", "fromYear": 2020, "toYear": 2025, "publicationType": "article", "sources": ["openalex", "crossref"] }`. Year/type fields may be null; types are `article`, `preprint`, and `book-chapter`.
- `PATCH /api/research/projects/{id}/papers/{paperId}`: `{ "decision": "INCLUDED", "reason": "Relevant population", "notes": "Review methods" }`.
- `GET /api/research/projects/{id}/export.csv` or `/export.bib`: all papers; append `?decision=INCLUDED` for included papers.

## Validation

From `backend`: `bash gradlew test bootJar`. From `frontend`: `npm run build` and `npm run lint`. Research integration tests use actual local HTTP source fixtures and SQL storage to exercise deduplication, metadata normalization, filters, source failures, project isolation, screening persistence, and scoped exports. Existing crawler tests remain in the suite.

# Research materials inventory

The Materials workspace answers a focused question: **Which research materials are explicitly linked from these paper pages, and what can I verify about them?** It is a bounded first prototype of paper → materials → researcher-verified inventory. Literature discovery and the general crawler remain available in separate tabs.

## Use it

1. Open **Research Materials**, name an inventory, and paste one to ten HTML paper URLs or bare DOIs, one per line. Prefer an article's HTML page over its PDF. DOIs are converted to DOI resolver URLs; redirects are checked individually.
2. Choose **Find linked materials**. A background scan saves progress and checks candidate destinations. You can leave the tab and return to the saved inventory.
3. Inspect **Paper page outcomes** and scan coverage. These explain source access failures, unsupported formats, and omitted work.
4. Expand a resource to see its original paper URL, actual source page, anchor text, surrounding context, discovery reason, and timestamp. Suggested types are Dataset, Code, Supplement, Protocol, Project page, and Repository record.
5. Open the resource, assess its relevance/contents, then save a decision: Unreviewed, Verified by researcher, Rejected, or Follow up. Add notes about versions, missing files, permission requirements, or next actions. Reviews are available after the scan finishes.
6. Export all candidates or only verified resources as CSV. Export scope is independent of on-screen filters. During a scan, exports are partial snapshots.

Papers in the Research Library also have a **Find linked materials** button that prefills a new scan. Check that the supplied record URL is the article page you want to inspect; a bibliographic index page may not contain the publisher's materials links.

## What is checked

Discovery uses explicit link text, paths, known repository hosts, and narrowly identified availability statements. It skips navigation/footer/reference sections where identifiable. A generic repository record remains a repository candidate rather than being asserted to contain a dataset. Candidate links are not proof that materials belong to the study; researcher review is necessary.

Resources are deduplicated by normalized linked URL, with fragments removed and query parameters retained. Multiple paper/source evidence entries remain attached to the same resource. Different versions or signed query strings are not silently collapsed. The CSV contains aligned multiline evidence columns; enable Wrap Text in your spreadsheet.

A HEAD request checks the destination, with a header-only GET fallback when HEAD returns 405/501. Redirects are bounded and validated. The report distinguishes:

| Result | Meaning |
| --- | --- |
| HTTP responded | An HTTP 2xx response was observed; contents, relevance and licensing remain unverified |
| Not found | HTTP 404 or 410 from the destination |
| Access denied | HTTP 401/402/403; denial may come from the site or network |
| Rate limited | HTTP 429; no bypass attempted |
| Server error | HTTP 5xx; not proof of permanent unavailability |
| Access unknown | Network/timeout/redirect/policy failure, or an inconclusive response |
| Not checked | Request/time budget left this candidate unchecked |

“No materials found” means no matching explicit links were found in the HTML pages successfully scanned. It does **not** mean the paper has no materials. Similarly, “scan complete” describes job completion, not complete coverage of the research record.

## Limits of this first version

- Ten supplied paper URLs; up to 100 distinct candidate resources and 30 evidence entries per resource.
- Up to 20 resource checks with a 90-second scheduling budget. An in-flight bounded request can extend beyond that budget. Unchecked candidates remain in the report.
- Two concurrent background jobs with an eight-job queue; excess submissions receive HTTP 429.
- HTML source bodies are bounded to 2 MB. PDF parsing, JavaScript rendering, recursive crawling of project pages, paywall/login access, citation traversal, file-content inspection and reuse-license verification are outside this version.
- Robots.txt is consulted before requests. Basic user-agent/Allow/Disallow matching, wildcards and end anchors are supported. Unavailable/denying robots policy is recorded as unknown coverage; it is not bypassed. Per-host requests are paced.
- Supported destinations are public HTTP(S) sites on standard ports, without URL credentials. Local/private addresses are rejected. Redirect targets are checked again. The trusted egress proxy, when configured, performs hostname resolution and must enforce public-destination/IP policy; do not deploy behind a proxy granting unintended private-network access. The loopback override is for isolated tests only and defaults to false.
- No claim of novel discovery quality or researcher time savings has been established yet. The pilot below is the next product-validation step.

## Storage and startup

The workspace uses the same configured H2/PostgreSQL database as research projects. Flyway migration V2 adds a separate inventory table; existing research projects are preserved. Progress, checks, reviews and evidence persist. Jobs interrupted by an application restart are marked **Interrupted**, preserving saved partial results; create a new scan to retry. Running jobs are not falsely marked successful or automatically reissued after a restart.

Use the existing backend/frontend startup described in [academic-research.md](academic-research.md). No new service, dependency or secret is required. Keep `materials.allow-loopback=false` (the default) for normal use. `materials.allow-loopback=true` is used only for the local HTTP fixture tests.

This managed environment requires paper and resource hosts to be allowed in its network settings. `arxiv.org` and `pmc.ncbi.nlm.nih.gov` have been added to the draft for the pilot, preserving previous entries. Linked repositories/publishers may require their own domain additions. Saving that draft does not apply network settings. Actual outbound checks to arXiv and PMC were denied by the environment proxy during implementation; that is not evidence of either publisher blocking this application.

## Reports and API

CSV uses one resource per row, stable inventory/resource IDs, scan state, suggested type, original/final URLs, HTTP status/result/details, check time, manual review/notes, and aligned source-paper/page/link/context/reason/discovery-time columns. All exported timestamps use UTC. UTF-8 with a BOM, quoted multiline fields, escaped quotes, and formula-neutralized text support spreadsheet reporting. The report preserves unchecked candidates and access uncertainty.

- `POST /api/materials/inventories`: `{ "name": "Study review", "paperUrls": ["https://example.org/paper", "10.1234/example"] }` → HTTP 202 and a queued inventory.
- `GET /api/materials/inventories`: summaries.
- `GET /api/materials/inventories/{id}`: current progress, paper outcomes, findings and coverage warnings. Poll only while Queued/Scanning.
- `PATCH /api/materials/inventories/{id}/findings/{findingId}`: `{ "decision": "VERIFIED", "notes": "Checked version and variables" }`.
- `GET /api/materials/inventories/{id}/export.csv`: all candidates; append `?review=VERIFIED` for verified-only output.

## Ten-paper pilot — prepared, not yet executed on live papers

Use these varied machine-learning papers as an initial convenience sample, not a representative evaluation of all academic fields:

| Paper | HTML landing page |
| --- | --- |
| Attention Is All You Need | https://arxiv.org/abs/1706.03762 |
| BERT | https://arxiv.org/abs/1810.04805 |
| Language Models are Few-Shot Learners | https://arxiv.org/abs/2005.14165 |
| Learning Transferable Visual Models From Natural Language Supervision | https://arxiv.org/abs/2103.00020 |
| An Image is Worth 16x16 Words | https://arxiv.org/abs/2010.11929 |
| Denoising Diffusion Probabilistic Models | https://arxiv.org/abs/2006.11239 |
| High-Resolution Image Synthesis with Latent Diffusion Models | https://arxiv.org/abs/2112.10752 |
| Exploring the Limits of Transfer Learning with a Unified Text-to-Text Transformer | https://arxiv.org/abs/1910.10683 |
| Training language models to follow instructions with human feedback | https://arxiv.org/abs/2203.02155 |
| Llama 2: Open Foundation and Fine-Tuned Chat Models | https://arxiv.org/abs/2307.09288 |

After network access is applied, scan these pages, then inspect each page manually to establish an explicit-link baseline. Record useful true positives, irrelevant candidates, missed explicit links, access-check failures, and manual discovery time versus assisted review time. Separate resources absent from the landing page from extraction failures; this version cannot discover links present only inside a PDF. Include a biomedical/PMC follow-up sample before generalizing. A no-link result is informative about the chosen entry page, not proof that research materials are absent.

Implementation validation used ten controlled paper pages with shared resources, plus separate empty/denied/PDF, policy, budget, and interruption scenarios. It verifies application behavior, **not** real-world recall or research usefulness. Live-pilot execution remains blocked on network access.

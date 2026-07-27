# Redis Performance Retrofit Findings

## Tooling
- codebase-memory-mcp project index exists for backend and frontend.
- Broad `search_code` calls timed out at 300s, so discovery fell back to targeted `rg`.

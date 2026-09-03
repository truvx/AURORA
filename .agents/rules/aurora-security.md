# AURORA Security Rules

- **Secrets**: Do not expose or hardcode secrets, tokens, cookies, or credentials.
- **Isolation**: Do not fabricate AI authentication or attempt to read another application's credentials (e.g., standard Gemini/ChatGPT app sessions).
- **Storage**: Use secure storage or server secret management for provider tokens. Never store tokens in the standard Room/IndexedDB database.
- **Validation**: Validate all external, user, provider, file, and AI data at boundaries.

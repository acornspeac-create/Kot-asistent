# KOT Assistant AI Server

This service is ready for Railway.

## Railway

Use this repository and set the service root directory to:

`/server`

The included `railway.toml` configures:

- Railpack build
- `npm start`
- `/health` health check
- restart on failure

## Required variables

Set these as Railway Variables, never in GitHub source code:

- `OPENAI_API_KEY` — OpenAI API project key
- `OPENAI_MODEL` — defaults to `gpt-5.6-luna`
- `KOT_SERVER_TOKEN` — a long random secret used by the Android app
- `PORT` — Railway supplies this automatically

## Endpoints

- `GET /health` — public health status, contains no secrets
- `POST /assistant` — protected by `Authorization: Bearer <KOT_SERVER_TOKEN>`

The assistant can use OpenAI web search automatically when useful.

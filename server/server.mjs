import http from "node:http";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || "gpt-5.6-luna";

function sendJson(res, status, data) {
  const body = JSON.stringify(data);

  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
  });

  res.end(body);
}

async function readJson(req) {
  let body = "";

  for await (const chunk of req) {
    body += chunk;

    if (body.length > 1_000_000) {
      throw new Error("Request too large");
    }
  }

  return JSON.parse(body || "{}");
}

function extractOutputText(response) {
  for (const item of response.output || []) {
    for (const content of item.content || []) {
      if (content.type === "output_text" && content.text) {
        return content.text;
      }
    }
  }

  return "";
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    return sendJson(res, 200, {
      ok: true,
      model,
      web_search: true,
    });
  }

  if (req.method !== "POST" || req.url !== "/assistant") {
    return sendJson(res, 404, {
      error: "Not found",
    });
  }

  if (!apiKey) {
    return sendJson(res, 500, {
      error: "OPENAI_API_KEY is not configured on the server",
    });
  }

  try {
    const payload = await readJson(req);
    const text = String(payload.text || "").trim();
    const history = String(payload.history || "").trim();

    if (!text) {
      return sendJson(res, 400, {
        error: "text is required",
      });
    }

    const inputParts = [];

    if (history) {
      inputParts.push("Recent local conversation:\n" + history);
    }

    inputParts.push("Current user request:\n" + text);

    const openaiResponse = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + apiKey,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        store: false,
        tools: [
          {
            type: "web_search",
            search_context_size: "medium",
          },
        ],
        tool_choice: "auto",
        instructions:
          "You are KOT Assistant, a private personal AI assistant. Be useful, concise, action-oriented, and reply in the user's language. You have web search available and may use it automatically whenever fresh or external information would help; do not ask for permission before ordinary web research. Never claim an external action happened unless a connected tool actually completed it. Never expose secrets, API keys, private tokens, or credentials.",
        input: inputParts.join("\n\n"),
      }),
    });

    const data = await openaiResponse.json();

    if (!openaiResponse.ok) {
      return sendJson(res, openaiResponse.status, {
        error:
          data &&
          data.error &&
          data.error.message
            ? data.error.message
            : "OpenAI request failed",
      });
    }

    const answer = extractOutputText(data);

    return sendJson(res, 200, {
      text: answer || "Ответ получен, но текст не найден.",
      response_id: data.id || null,
    });
  } catch (error) {
    return sendJson(res, 500, {
      error: error instanceof Error ? error.message : String(error),
    });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log("KOT Assistant server listening on :" + port);
});

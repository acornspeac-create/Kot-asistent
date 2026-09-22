import fs from "node:fs/promises";
import path from "node:path";

const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.SELF_IMPROVE_MODEL || "gpt-5.6-luna";
const root = process.env.GITHUB_WORKSPACE || process.cwd();

const allowedPaths = [
  "app/src/main/java/tj/kod/assistant/MainActivity.kt",
  "app/src/main/java/tj/kod/assistant/AssistantViewModel.kt",
  "app/src/main/java/tj/kod/assistant/MemoryStore.kt",
  "app/src/main/java/tj/kod/assistant/Message.kt",
  "README.md",
];

const forbiddenFragments = [
  "android.permission.",
  "REQUEST_INSTALL_PACKAGES",
  "MANAGE_EXTERNAL_STORAGE",
  "BIND_ACCESSIBILITY_SERVICE",
  "DevicePolicyManager",
  "AccessibilityService",
  "PackageInstaller",
  "Runtime.getRuntime",
  "ProcessBuilder",
  "java.net.",
  "okhttp",
  "WebView",
  "System.getenv",
  "OPENAI_API_KEY",
  "GITHUB_TOKEN",
  "Authorization:",
  "Bearer ",
  "private key",
  "ssh-rsa",
];

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

async function loadSources() {
  const parts = [];

  for (const file of allowedPaths) {
    const absolute = path.join(root, file);
    const content = await fs.readFile(absolute, "utf8");

    parts.push(
      [
        "FILE: " + file,
        "-----",
        content,
        "-----",
      ].join("\n"),
    );
  }

  return parts.join("\n\n");
}

function validateProposal(file) {
  if (!allowedPaths.includes(file.path)) {
    throw new Error("Model tried to modify a non-whitelisted file: " + file.path);
  }

  if (typeof file.content !== "string" || file.content.length > 120_000) {
    throw new Error("Invalid replacement content for: " + file.path);
  }

  if (file.path.endsWith(".kt")) {
    for (const fragment of forbiddenFragments) {
      if (file.content.includes(fragment)) {
        throw new Error(
          "Safety gate blocked " + file.path + " because it introduced: " + fragment,
        );
      }
    }

    const urls = file.content.match(/https?:\/\/[^"')\s]+/g) || [];
    const allowedDocumentationUrl = "https://example.com";

    for (const url of urls) {
      if (!url.startsWith(allowedDocumentationUrl)) {
        throw new Error(
          "Safety gate blocked an unexpected network destination in " + file.path,
        );
      }
    }
  }
}

async function main() {
  if (!apiKey) {
    console.log(
      "OPENAI_API_KEY is not configured. Autonomous coding is installed but dormant.",
    );
    return;
  }

  const sources = await loadSources();

  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      summary: {
        type: "string",
      },
      files: {
        type: "array",
        maxItems: 3,
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            path: {
              type: "string",
              enum: allowedPaths,
            },
            content: {
              type: "string",
            },
            reason: {
              type: "string",
            },
          },
          required: ["path", "content", "reason"],
        },
      },
    },
    required: ["summary", "files"],
  };

  const prompt = [
    "Improve KOT Assistant autonomously.",
    "",
    "Goal: make the personal Android assistant more reliable, pleasant, useful offline,",
    "and easier to use. You may research current best practices on the web.",
    "",
    "Hard boundaries:",
    "- Only modify files in the supplied whitelist.",
    "- Do not add permissions, network endpoints, background surveillance, credential access,",
    "  package installation logic, device administration, accessibility automation, shell execution,",
    "  or any mechanism that bypasses Android security.",
    "- Do not weaken security or privacy.",
    "- Do not remove existing features.",
    "- Prefer one small, testable improvement per run.",
    "- Return complete replacement contents only for files that truly need a change.",
    "- If no safe improvement is justified, return an empty files array.",
    "",
    "Current files:",
    sources,
  ].join("\n");

  const response = await fetch("https://api.openai.com/v1/responses", {
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
          search_context_size: "low",
        },
      ],
      tool_choice: "auto",
      text: {
        format: {
          type: "json_schema",
          name: "kot_self_improvement",
          strict: true,
          schema,
        },
      },
      instructions:
        "Act as a conservative maintainer. Produce small safe code improvements only. Never expand privileges, network destinations, or access to private data.",
      input: prompt,
    }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(
      data && data.error && data.error.message
        ? data.error.message
        : "OpenAI self-improvement request failed",
    );
  }

  const output = extractOutputText(data);

  if (!output) {
    throw new Error("Self-improvement model returned no structured output");
  }

  const plan = JSON.parse(output);

  console.log("Autonomous maintenance plan: " + plan.summary);

  let changed = 0;

  for (const file of plan.files) {
    validateProposal(file);

    const absolute = path.join(root, file.path);
    const current = await fs.readFile(absolute, "utf8");

    if (current === file.content) {
      continue;
    }

    await fs.writeFile(absolute, file.content, "utf8");
    changed += 1;

    console.log("Updated " + file.path + ": " + file.reason);
  }

  console.log("Files changed: " + changed);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

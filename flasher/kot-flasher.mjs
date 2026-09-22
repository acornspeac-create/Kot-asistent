import http from "node:http";
import { spawnSync } from "node:child_process";
import { createHash, timingSafeEqual } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import { basename, dirname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.KOT_FLASHER_PORT || 8791);
const host = String(process.env.KOT_FLASHER_HOST || "127.0.0.1");
const token = String(process.env.KOT_FLASHER_TOKEN || "");
const adb = process.env.ADB_PATH || "adb";
const fastboot = process.env.FASTBOOT_PATH || "fastboot";
const heimdall = process.env.HEIMDALL_PATH || "heimdall";
let rememberedSamsung = null;

function run(bin, args, timeout = 15000) {
  const result = spawnSync(bin, args, {
    encoding: "utf8",
    timeout,
    windowsHide: true,
  });

  return {
    ok: result.status === 0,
    status: result.status,
    stdout: String(result.stdout || "").trim(),
    stderr: String(result.stderr || "").trim(),
    error: result.error ? String(result.error.message || result.error) : "",
    command: [bin, ...args].join(" "),
  };
}

function toolAvailable(bin) {
  return run(bin, ["version"], 5000).ok;
}

function parseAdbDevices(text) {
  return text
    .split(/\r?\n/)
    .slice(1)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [serial, state, ...rest] = line.split(/\s+/);
      const meta = {};
      for (const part of rest) {
        const i = part.indexOf(":");
        if (i > 0) meta[part.slice(0, i)] = part.slice(i + 1);
      }
      return { transport: "adb", serial, state, ...meta };
    });
}

function parseFastbootDevices(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [serial] = line.split(/\s+/);
      return { transport: "fastboot", serial, state: "fastboot" };
    });
}

function detectVendor(...parts) {
  const text = parts.filter(Boolean).join(" ").toLowerCase();

  if (/samsung|sm-[a-z0-9]+/i.test(text)) return "samsung";
  if (/xiaomi|redmi|poco/.test(text)) return "xiaomi";
  if (/google|pixel/.test(text)) return "google";
  if (/oneplus/.test(text)) return "oneplus";
  if (/motorola|moto\s|lenovo/.test(text)) return "motorola";
  if (/oppo/.test(text)) return "oppo";
  if (/realme/.test(text)) return "realme";
  if (/vivo|iqoo/.test(text)) return "vivo";
  if (/nothing/.test(text)) return "nothing";
  if (/sony|xperia/.test(text)) return "sony";
  if (/huawei/.test(text)) return "huawei";
  if (/honor/.test(text)) return "honor";

  return "unknown";
}

function routeDevice(device) {
  const vendor = device.vendor || detectVendor(
    device.manufacturer,
    device.model,
    device.product,
    device.device,
  );

  if (device.transport === "heimdall" || vendor === "samsung") {
    return {
      ...device,
      vendor: "samsung",
      driver: "samsung-heimdall",
      flashMode: "download",
    };
  }

  if (device.transport === "fastboot") {
    const driver =
      vendor === "unknown"
        ? "generic-fastboot"
        : vendor + "-fastboot";

    return {
      ...device,
      vendor,
      driver,
      flashMode: "fastboot",
    };
  }

  return {
    ...device,
    vendor,
    driver:
      vendor === "samsung"
        ? "samsung-heimdall"
        : vendor === "unknown"
          ? "generic-fastboot"
          : vendor + "-fastboot",
    flashMode: vendor === "samsung" ? "download" : "bootloader",
  };
}

function listDevices() {
  const tools = {
    adb: toolAvailable(adb),
    fastboot: toolAvailable(fastboot),
    heimdall: toolAvailable(heimdall),
  };
  const devices = [];

  if (tools.adb) {
    const r = run(adb, ["devices", "-l"]);
    if (r.ok) {
      devices.push(
        ...parseAdbDevices(r.stdout).map((device) => routeDevice(device))
      );
    }
  }

  if (tools.fastboot) {
    const r = run(fastboot, ["devices"]);
    if (r.ok) {
      devices.push(
        ...parseFastbootDevices(r.stdout).map((device) => routeDevice(device))
      );
    }
  }

  if (tools.heimdall) {
    const detected = run(heimdall, ["detect"], 8000);
    if (detected.ok) {
      devices.push(
        routeDevice({
          transport: "heimdall",
          serial: "SAMSUNG-DOWNLOAD",
          state: "download",
          manufacturer: "Samsung",
          model: rememberedSamsung?.model || "",
          product: rememberedSamsung?.product || "",
          device: rememberedSamsung?.device || "",
        })
      );
    }
  }

  return { tools, devices };
}

function adbProp(serial, key) {
  const r = run(adb, ["-s", serial, "shell", "getprop", key]);
  return r.ok ? r.stdout.trim() : "";
}

function adbBattery(serial) {
  const r = run(adb, ["-s", serial, "shell", "dumpsys", "battery"]);
  if (!r.ok) return null;
  const m = r.stdout.match(/level:\s*(\d+)/i);
  return m ? Number(m[1]) : null;
}

function fastbootVar(serial, key) {
  const r = run(fastboot, ["-s", serial, "getvar", key]);
  const all = [r.stdout, r.stderr].filter(Boolean).join("\n");
  const escaped = key.replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&");
  const m = all.match(new RegExp("(?:^|\\n)\\s*(?:\\(bootloader\\)\\s*)?" + escaped + "\\s*:\\s*([^\\r\\n]+)", "i"));
  return m ? m[1].trim() : "";
}

function inspectDevice(serial) {
  if (!serial) throw new Error("serial is required");

  const inventory = listDevices();
  const device = inventory.devices.find((x) => x.serial === serial);
  if (!device) throw new Error("Device not found: " + serial);

  if (device.transport === "adb") {
    const inspected = routeDevice({
      ...device,
      manufacturer: adbProp(serial, "ro.product.manufacturer"),
      model: adbProp(serial, "ro.product.model"),
      product: adbProp(serial, "ro.build.product") || adbProp(serial, "ro.product.device"),
      device: adbProp(serial, "ro.product.device"),
      fingerprint: adbProp(serial, "ro.build.fingerprint"),
      android: adbProp(serial, "ro.build.version.release"),
      battery: adbBattery(serial),
    });

    if (inspected.vendor === "samsung") {
      rememberedSamsung = inspected;
    }

    return inspected;
  }

  if (device.transport === "heimdall") {
    return routeDevice({
      ...device,
      manufacturer: "Samsung",
      model: rememberedSamsung?.model || "",
      product: rememberedSamsung?.product || "",
      device: rememberedSamsung?.device || "",
      rememberedFromAdb: Boolean(rememberedSamsung),
    });
  }

  const unlockedRaw = fastbootVar(serial, "unlocked");
  return routeDevice({
    ...device,
    product: fastbootVar(serial, "product") || device.product || "",
    currentSlot: fastbootVar(serial, "current-slot"),
    unlocked: /yes|true|1/i.test(unlockedRaw),
    unlockedRaw,
    secure: fastbootVar(serial, "secure"),
  });
}

function sha256(path) {
  const hash = createHash("sha256");
  hash.update(readFileSync(path));
  return hash.digest("hex");
}

function safeResolve(base, relative) {
  const root = resolve(base);
  const full = resolve(root, relative);
  if (full !== root && !full.startsWith(root + sep)) {
    throw new Error("Path escapes firmware directory: " + relative);
  }
  return full;
}

function loadManifest(pathArg) {
  if (!pathArg) throw new Error("manifest path is required");
  const manifestPath = resolve(pathArg);
  const raw = JSON.parse(readFileSync(manifestPath, "utf8"));
  const firmwareDir = resolve(dirname(manifestPath), raw.firmwareDir || ".");

  if (!Array.isArray(raw.partitions) || raw.partitions.length === 0) {
    throw new Error("Manifest must contain partitions[]");
  }

  return { ...raw, manifestPath, firmwareDir };
}

function validateManifest(manifest) {
  const errors = [];
  const checked = [];

  for (const part of manifest.partitions) {
    const name = String(part?.name || "");
    if (!/^[a-zA-Z0-9_.-]+$/.test(name)) {
      errors.push("Invalid partition name: " + name);
      continue;
    }

    if (!part.file) {
      errors.push("Missing file for partition " + name);
      continue;
    }

    let full;
    try {
      full = safeResolve(manifest.firmwareDir, String(part.file));
    } catch (e) {
      errors.push(e instanceof Error ? e.message : String(e));
      continue;
    }

    if (!existsSync(full) || !statSync(full).isFile()) {
      errors.push("Missing image: " + part.file);
      continue;
    }

    const actual = sha256(full);
    if (part.sha256 && actual.toLowerCase() !== String(part.sha256).toLowerCase()) {
      errors.push("SHA-256 mismatch: " + part.file);
    }

    checked.push({
      partition: name,
      file: basename(full),
      sha256: actual,
      bytes: statSync(full).size,
    });
  }

  return { ok: errors.length === 0, errors, checked };
}

function ensureProductMatches(device, manifest) {
  const allowed = Array.isArray(manifest.products)
    ? manifest.products.map(String)
    : [];

  if (allowed.length === 0) {
    throw new Error("Manifest must declare products[]");
  }

  if (!device.product || !allowed.includes(device.product)) {
    throw new Error(
      "Product mismatch. Device=" +
        (device.product || "unknown") +
        ", manifest=" +
        allowed.join(",")
    );
  }
}

function flash(serial, manifestPath, execute) {
  const manifest = loadManifest(manifestPath);
  const validation = validateManifest(manifest);

  if (!validation.ok) {
    return { ok: false, stage: "validate", ...validation };
  }

  const device = inspectDevice(serial);

  if (device.transport !== "fastboot") {
    return {
      ok: false,
      stage: "mode",
      error: "Device must be in fastboot mode",
      device,
      validation,
    };
  }

  ensureProductMatches(device, manifest);

  if (!device.unlocked) {
    return {
      ok: false,
      stage: "bootloader",
      error:
        "Bootloader is not reported as unlocked. KOT Flasher will not bypass OEM/FRP locks.",
      device,
      validation,
    };
  }

  const plan = manifest.partitions.map((part) => ({
    command: "fastboot",
    args: [
      "-s",
      serial,
      "flash",
      String(part.name),
      safeResolve(manifest.firmwareDir, String(part.file)),
    ],
  }));

  if (!execute) {
    return { ok: true, dryRun: true, device, validation, plan };
  }

  const log = [];

  for (const step of plan) {
    const result = run(
      step.command,
      step.args,
      Number(manifest.stepTimeoutMs || 180000)
    );
    log.push(result);

    if (!result.ok) {
      return {
        ok: false,
        stage: "flash",
        failedPartition: step.args[3],
        device,
        validation,
        log,
      };
    }
  }

  if (manifest.wipe === true) {
    const wipe = run(fastboot, ["-s", serial, "-w"], 180000);
    log.push(wipe);
    if (!wipe.ok) {
      return { ok: false, stage: "wipe", device, validation, log };
    }
  }

  if (manifest.reboot !== false) {
    log.push(run(fastboot, ["-s", serial, "reboot"], 30000));
  }

  return { ok: true, dryRun: false, device, validation, log };
}

function isAuthorized(req) {
  if (!token) return false;

  const header = String(req.headers.authorization || "");
  if (!header.startsWith("Bearer ")) return false;

  const supplied = Buffer.from(header.slice(7));
  const expected = Buffer.from(token);

  return (
    supplied.length === expected.length &&
    timingSafeEqual(supplied, expected)
  );
}

async function bodyJson(req) {
  let body = "";

  for await (const chunk of req) {
    body += chunk;
    if (body.length > 2_000_000) throw new Error("Request too large");
  }

  return JSON.parse(body || "{}");
}

function send(res, status, data) {
  const body = JSON.stringify(data, null, 2);

  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store",
  });

  res.end(body);
}

function startServer() {
  const server = http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/health") {
        return send(res, 200, {
          ok: true,
          name: "KOT Flasher Agent",
          version: "0.1.0",
          ...listDevices(),
        });
      }

      if (!isAuthorized(req)) {
        return send(res, 401, { error: "Unauthorized" });
      }

      if (req.method === "GET" && req.url === "/devices") {
        return send(res, 200, listDevices());
      }

      if (req.method === "POST" && req.url === "/inspect") {
        const body = await bodyJson(req);
        return send(res, 200, inspectDevice(String(body.serial || "")));
      }

      if (req.method === "POST" && req.url === "/reboot") {
        const body = await bodyJson(req);
        const serial = String(body.serial || "");
        const target = String(body.target || "system");
        const device = inspectDevice(serial);

        let result;
        if (device.transport === "adb") {
          const args = ["-s", serial, "reboot"];
          if (target === "bootloader" || target === "recovery") {
            args.push(target);
          } else if (target !== "system") {
            return send(res, 400, { error: "Unsupported reboot target" });
          }
          result = run(adb, args);
        } else {
          if (target !== "system") {
            return send(res, 400, {
              error: "Fastboot reboot currently supports system only",
            });
          }
          result = run(fastboot, ["-s", serial, "reboot"]);
        }

        return send(res, result.ok ? 200 : 500, result);
      }

      if (req.method === "POST" && req.url === "/flash") {
        const body = await bodyJson(req);
        const serial = String(body.serial || "");
        const manifestPath = String(body.manifestPath || "");
        const execute = body.execute === true;

        if (
          execute &&
          String(body.confirmation || "") !== "FLASH " + serial
        ) {
          return send(res, 400, {
            error: "Confirmation must be exactly: FLASH " + serial,
          });
        }

        const result = flash(serial, manifestPath, execute);
        return send(res, result.ok ? 200 : 400, result);
      }

      return send(res, 404, { error: "Not found" });
    } catch (e) {
      return send(res, 500, {
        error: e instanceof Error ? e.message : String(e),
      });
    }
  });

  server.listen(port, host, () => {
    console.log(
      "KOT Flasher Agent listening on http://" + host + ":" + port
    );
    console.log("ADB:", adb, "Fastboot:", fastboot);

    if (!token) {
      console.log(
        "Set KOT_FLASHER_TOKEN before using protected endpoints."
      );
    }
  });
}

function usage() {
  console.log(
    "KOT Flasher Agent\\n\\n" +
      "Commands:\\n" +
      "  node kot-flasher.mjs devices\\n" +
      "  node kot-flasher.mjs inspect <serial>\\n" +
      "  node kot-flasher.mjs plan <serial> <manifest.json>\\n" +
      "  node kot-flasher.mjs flash <serial> <manifest.json> --execute\\n" +
      "  node kot-flasher.mjs serve\\n\\n" +
      "Important:\\n" +
      "  - Install Android platform-tools (adb + fastboot).\\n" +
      "  - Bootloader unlocking is NOT automated.\\n" +
      "  - FRP/OEM/account locks are NOT bypassed.\\n" +
      "  - Flash only firmware that exactly matches the device product.\\n"
  );
}

const [cmd, ...args] = process.argv.slice(2);

if (!cmd || cmd === "help" || cmd === "--help") {
  usage();
} else if (cmd === "devices") {
  console.log(JSON.stringify(listDevices(), null, 2));
} else if (cmd === "inspect") {
  console.log(JSON.stringify(inspectDevice(args[0]), null, 2));
} else if (cmd === "plan") {
  console.log(JSON.stringify(flash(args[0], args[1], false), null, 2));
} else if (cmd === "flash") {
  if (!args.includes("--execute")) {
    throw new Error(
      "Add --execute after reviewing the dry-run plan."
    );
  }

  console.log(JSON.stringify(flash(args[0], args[1], true), null, 2));
} else if (cmd === "serve") {
  startServer();
} else {
  usage();
}

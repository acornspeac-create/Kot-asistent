const fs = require("fs");
const vm = require("vm");

const html = fs.readFileSync("simple-game/app/src/main/assets/index.html", "utf8");
const start = html.lastIndexOf("<script>") + "<script>".length;
const end = html.lastIndexOf("</script>");
if (start < "<script>".length || end <= start) throw new Error("Game script not found");
const code = html.slice(start, end);

// Catch the exact class of bug that broke 2.3/2.4.
const badForEach = [...code.matchAll(/(?<!\$)\$\("([^"]+)"\)\.forEach/g)].map(m => m[1]);
if (badForEach.length) {
  throw new Error("Single-element $() used with forEach: " + badForEach.join(", "));
}

const listeners = new Map();
const elements = new Map();

function makeClassList() {
  const set = new Set();
  return {
    add(...xs) { xs.forEach(x => set.add(x)); },
    remove(...xs) { xs.forEach(x => set.delete(x)); },
    toggle(x, force) {
      if (force === undefined) {
        if (set.has(x)) { set.delete(x); return false; }
        set.add(x); return true;
      }
      if (force) set.add(x); else set.delete(x);
      return !!force;
    },
    contains(x) { return set.has(x); }
  };
}

function el(key) {
  if (!elements.has(key)) {
    const style = { setProperty(name, value) { this[name] = value; } };
    elements.set(key, {
      style,
      dataset: {},
      value: "",
      textContent: "",
      innerHTML: "",
      disabled: false,
      options: [],
      classList: makeClassList(),
      addEventListener(type, fn) { listeners.set(key + "::" + type, fn); },
      closest() { return null; },
      remove() {},
      appendChild() {},
      setAttribute() {},
      getAttribute() { return null; }
    });
  }
  return elements.get(key);
}

const document = {
  documentElement: { lang: "ru" },
  querySelector: selector => el(selector),
  querySelectorAll: () => [],
  createElement: tag => el("created:" + tag + ":" + elements.size)
};

const localStorage = {
  values: new Map(),
  getItem(k) { return this.values.get(k) || null; },
  setItem(k, v) { this.values.set(k, String(v)); },
  removeItem(k) { this.values.delete(k); }
};

const sandbox = {
  console,
  document,
  localStorage,
  navigator: { hardwareConcurrency: 8, deviceMemory: 8, vibrate() {} },
  innerWidth: 412,
  innerHeight: 915,
  setTimeout: () => 1,
  clearTimeout() {},
  setInterval: () => 1,
  clearInterval() {},
  Date, Math, JSON, Array, Object, String, Number, Boolean, parseFloat, parseInt, isNaN
};
sandbox.window = sandbox;
sandbox.globalThis = sandbox;
vm.createContext(sandbox);

try {
  vm.runInContext(code, sandbox, { filename: "game.js" });
} catch (e) {
  console.error("INIT_FAIL", e.stack || e);
  process.exit(1);
}

const play = listeners.get("#playBtn::click");
if (typeof play !== "function") {
  console.error("PLAY_HANDLER_MISSING");
  process.exit(2);
}

try {
  play({ stopPropagation() {}, clientX: 100, clientY: 100, target: el("event") });
} catch (e) {
  console.error("PLAY_FAIL", e.stack || e);
  process.exit(3);
}

if (el("#catTarget").style.display !== "block") {
  console.error("TARGET_NOT_SHOWN");
  process.exit(4);
}

const catHit = listeners.get("#catTarget::pointerdown");
if (typeof catHit !== "function") {
  console.error("CAT_HIT_HANDLER_MISSING");
  process.exit(5);
}

const beforeLeft = el("#catTarget").style.left;
const beforeTop = el("#catTarget").style.top;

try {
  catHit({ stopPropagation() {}, clientX: 180, clientY: 320, target: el("#catTarget") });
} catch (e) {
  console.error("CAT_HIT_FAIL", e.stack || e);
  process.exit(6);
}

const afterLeft = el("#catTarget").style.left;
const afterTop = el("#catTarget").style.top;
if (beforeLeft === afterLeft && beforeTop === afterTop) {
  console.error("TARGET_DID_NOT_MOVE_AFTER_HIT", { beforeLeft, beforeTop, afterLeft, afterTop });
  process.exit(7);
}

console.log("SMOKE_OK: Play starts gameplay and target relocates after a hit");

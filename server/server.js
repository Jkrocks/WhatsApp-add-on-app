// Sahi Bol server
// 1) WhatsApp bot: send/forward a message to the bot number -> fixed text + simple reasons
// 2) /fix API: used by the Sahi Bol keyboards (Android + iPhone)
// Node 18+ (built-in fetch)

import express from "express";

const {
  WHATSAPP_TOKEN,      // Meta access token (WhatsApp Cloud API)
  PHONE_NUMBER_ID,     // Meta > WhatsApp > API Setup
  VERIFY_TOKEN,        // any secret word, same as in Meta webhook settings
  ANTHROPIC_API_KEY,   // console.anthropic.com
  SAHIBOL_APP_KEY,     // any long secret, same one goes into the keyboard apps
  PORT = 3000,
} = process.env;

const app = express();
app.use(express.json({ limit: "50kb" }));

const lastText = new Map(); // last message per WhatsApp user, for tone buttons

app.get("/", (_req, res) => res.send("Sahi Bol is running ✅"));

// ---------- Keyboard API ----------
app.post("/fix", async (req, res) => {
  if (!SAHIBOL_APP_KEY || req.get("x-app-key") !== SAHIBOL_APP_KEY) return res.sendStatus(401);
  const text = String(req.body?.text || "").slice(0, 2000).trim();
  if (!text) return res.status(400).json({ error: "empty" });
  try {
    res.json(await fixText(text, req.body?.tone || "normal"));
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "fix_failed" });
  }
});

// ---------- WhatsApp webhook ----------
app.get("/webhook", (req, res) => {
  if (req.query["hub.mode"] === "subscribe" && req.query["hub.verify_token"] === VERIFY_TOKEN) {
    return res.send(req.query["hub.challenge"]);
  }
  res.sendStatus(403);
});

app.post("/webhook", async (req, res) => {
  res.sendStatus(200);
  try {
    const msg = req.body?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
    if (!msg) return;
    const from = msg.from;

    if (msg.type === "interactive") {
      const text = lastText.get(from);
      if (!text) return sendText(from, "Please send your message again.");
      return replyFix(from, text, msg.interactive.button_reply.id);
    }
    if (msg.type !== "text") return sendText(from, "Please send a text message. I will fix it for you ✍️");

    const text = msg.text.body.trim();
    if (/^(hi|hello|start|help)$/i.test(text)) {
      return sendText(from,
        "Namaste! I am *Sahi Bol* 👋\n\nSend or forward any message you want to send a customer. " +
        "I will fix the English and tell you *why* in simple words.");
    }
    lastText.set(from, text);
    await replyFix(from, text, "normal");
  } catch (e) {
    console.error(e);
  }
});

async function replyFix(to, text, tone) {
  let out;
  try {
    out = await fixText(text, tone);
  } catch {
    return sendText(to, "Sorry, something went wrong. Please try again.");
  }
  await sendText(to, out.fixed);
  const reasons = out.reasons.length
    ? out.reasons.map((x) => `• ~${x.from}~ → *${x.to}*: ${x.why}`).join("\n")
    : "Your message was already good 👍";
  await sendButtons(to, `✅ *Why:*\n${reasons}\n\nCopy the message above and send it to your customer.`);
}

// ---------- Claude ----------
async function fixText(text, tone) {
  const toneRule = {
    normal: "Keep the same meaning and a friendly selling tone.",
    polite: "Make it more polite and respectful.",
    short: "Make it short and clear, under 20 words.",
    hindi: "Write the fixed message in simple Hindi (Devanagari). Reasons stay in simple English.",
  }[tone] || "Keep the same meaning and a friendly selling tone.";

  const prompt = `You help small Indian sellers fix messages they send to customers on WhatsApp.
Fix grammar, spelling and clarity. ${toneRule}
Never use the word "cheap" (say "low price" instead). Keep prices, numbers and names the same.
Give at most 3 reasons. Each reason must be very simple, 6 words or less.

Reply ONLY with JSON like:
{"fixed":"...","reasons":[{"from":"old words","to":"new words","why":"simple reason"}]}

Message:
${text}`;

  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-5-5",
      max_tokens: 600,
      messages: [{ role: "user", content: prompt }],
    }),
  });
  if (!r.ok) throw new Error(`Claude ${r.status}: ${await r.text()}`);
  const data = await r.json();
  const raw = data?.content?.[0]?.text || "";
  const out = JSON.parse(raw.slice(raw.indexOf("{"), raw.lastIndexOf("}") + 1));
  return { fixed: String(out.fixed || text), reasons: Array.isArray(out.reasons) ? out.reasons.slice(0, 3) : [] };
}

// ---------- WhatsApp send ----------
async function wa(body) {
  const r = await fetch(`https://graph.facebook.com/v21.0/${PHONE_NUMBER_ID}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${WHATSAPP_TOKEN}`, "Content-Type": "application/json" },
    body: JSON.stringify({ messaging_product: "whatsapp", ...body }),
  });
  if (!r.ok) console.error("WhatsApp error:", await r.text());
}
const sendText = (to, body) => wa({ to, type: "text", text: { body } });
const sendButtons = (to, body) =>
  wa({
    to,
    type: "interactive",
    interactive: {
      type: "button",
      body: { text: body },
      action: {
        buttons: [
          { type: "reply", reply: { id: "polite", title: "More polite" } },
          { type: "reply", reply: { id: "short", title: "Shorter" } },
          { type: "reply", reply: { id: "hindi", title: "In Hindi" } },
        ],
      },
    },
  });

app.listen(PORT, () => console.log(`Sahi Bol running on port ${PORT}`));

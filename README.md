# Sahi Bol: fix your WhatsApp messages before you send them

Three parts, one folder:

| Folder | What it is | Where it runs |
|---|---|---|
| `server/` | The brain: fixes the text with Claude + the WhatsApp bot | Render.com (free) |
| `android/` | The **Sahi Bol keyboard** for Android | Your Android phone |
| `ios/` | The **Sahi Bol keyboard** for iPhone | Your iPhone |

Both keyboards work inside WhatsApp (and any other app): type your message, tap **✓ Fix**, check the reasons, tap **Use this**, send.

---

## Step 1: Put the server online (15 min, do this first)

1. Get a **Claude API key** at console.anthropic.com → API Keys.
2. Make up an **app key**: any long secret, e.g. `sahibol-8f3k29xq`. Write it down.
3. Upload this whole folder to a new **GitHub** repo.
4. On **render.com** → New → Web Service → pick the repo:
   - Root directory: `server`
   - Build command: `npm install`
   - Start command: `npm start`
   - Environment variables: `ANTHROPIC_API_KEY`, `SAHIBOL_APP_KEY`
     (add `WHATSAPP_TOKEN`, `PHONE_NUMBER_ID`, `VERIFY_TOKEN` only if you also want the WhatsApp bot)
5. Open your Render link. You should see **"Sahi Bol is running ✅"**. Copy the link.

## Step 2: Android app (10 min, no coding)

1. In your GitHub repo → **Settings › Secrets and variables › Actions** → add two secrets:
   - `SAHIBOL_SERVER_URL` = your Render link
   - `SAHIBOL_APP_KEY` = your app key
2. Go to the **Actions** tab → *Build Sahi Bol apps* → **Run workflow**.
3. When it turns green (about 5 min), open it and download **SahiBol-Android-APK**.
4. Unzip it and send `app-debug.apk` to your phone. Open it and allow "Install unknown apps".
5. Open **Sahi Bol** → tap *Open keyboard settings* → turn on Sahi Bol → tap *Choose keyboard*.

Done. Open WhatsApp, type, tap **✓ Fix**.

## Step 3: iPhone app (needs a Mac)

Apple only allows iPhone apps to be installed from a Mac with Xcode. There's no way around this.

1. On a Mac, install **Xcode** (App Store) and **XcodeGen** (`brew install xcodegen`).
2. Open `ios/project.yml` and put your Render link and app key in `SAHIBOL_SERVER_URL` and `SAHIBOL_APP_KEY`.
3. In Terminal: `cd ios && xcodegen`, then open `SahiBol.xcodeproj`.
4. Click each target (**SahiBol** and **SahiBolKeyboard**) → *Signing & Capabilities* → choose your Apple ID as Team.
5. Plug in your iPhone and press ▶ Run.
6. On the iPhone: Settings › General › Keyboard › Keyboards › Add New Keyboard › **Sahi Bol** → turn on **Allow Full Access**.

With a free Apple ID, the app works for 7 days, then you press Run again. With the paid Apple Developer account (₹8,700/year), you can send it to other sellers with TestFlight or the App Store.

## Optional: WhatsApp bot number

Sellers can also just forward a message to a bot number. See `server/server.js`.
Setup: Meta developer app → WhatsApp → webhook `https://YOUR-LINK.onrender.com/webhook`, verify token = your `VERIFY_TOKEN`, subscribe to **messages**.

## Good to know
- The free Render plan sleeps when unused, so the first fix after a while can take 30–50 seconds. The paid plan ($7/month) stays awake.
- The keyboard fixes up to 2,000 characters at a time (the whole message box).
- Each fix costs a very small amount of Claude credit.

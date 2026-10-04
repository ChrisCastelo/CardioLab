# CardioLab design archive

`cardiolab-console-mockup.html` is the interactive fragment developed in the conversation. `cardiolab-console-preview.html` is its already-rendered standalone preview; open it in a browser. The wrapper loads approved public CDN dependencies and needs Internet access. All readings and integrations in this mockup are simulated.

This snapshot predates the final native track and interval-bar refinements. The **Android source and screenshots are authoritative**: counterclockwise track beginning at the bottom-right finish line, vertically aligned split guides, interval switch beside Resume/Pause, interval phase below the sensor-status line, and metrics only at the bottom. See `docs/images/cardiolab-interval-header.png`.

Optional browser smoke check, from the repository root with Node.js available:

```powershell
npm install --prefix tools --no-save --package-lock=false playwright
npx --prefix tools playwright install chromium
node tools/verify-console-mockup.cjs
```

Alternatively set `PLAYWRIGHT_MODULE` to an existing Playwright module and `PLAYWRIGHT_EXECUTABLE_PATH` to an existing compatible Chromium executable. Screenshots go to ignored `artifacts/mockup-verification/`. This tests the archived design only; Android checks are separate.

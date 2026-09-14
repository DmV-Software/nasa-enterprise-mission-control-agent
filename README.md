# NASA Enterprise Mission Control Agent

A NASA multi-domain research agent built with LangChain4j + Gemini, submitted to the micro1
Agentic Workflows Hackathon.

**Solution video:** https://youtu.be/v_EfBnrCQ-s

## Who has this problem

**Space Systems Data Analyst / Aerospace Research Engineer** — someone who has to pull together
evidence from multiple NASA data sources to answer one real question, not just look up one fact.

## The bottleneck

NASA's public data is real but fragmented: Near-Earth Object tracking, Astronomy Picture of the
Day, Mars rover telemetry, DONKI space weather feeds, Earth EPIC imagery, the Exoplanet Archive,
and TechPort each live behind their own endpoint with their own JSON shape. A concrete example of
the actual workflow this creates:

> An analyst preparing a weekly NEO threat briefing has to: (1) hit `/neo/rest/v1/feed` for a date
> range, (2) manually find the object with the largest estimated diameter, (3) pull its mass and
> velocity out of a nested JSON structure, (4) run an impact-energy calculation by hand or in a
> spreadsheet, (5) repeat for space weather correlation across three separate DONKI endpoints, and
> (6) format all of it into a report — while working around NASA's public rate limits (the
> `DEMO_KEY` tier is capped at ~30 requests/hour), which can silently truncate a session halfway
> through and lose the context gathered so far.

The actual cost isn't "the data doesn't exist" — it's the manual cross-endpoint stitching, error
handling, and formatting overhead every time this workflow repeats.

## How the agent solves it

| Bottleneck step | Agent capability |
|---|---|
| Manually calling 10 different endpoints | 10 tools behind one natural-language interface (LangChain4j `AiServices` + `@Tool`) |
| Manually chaining NEO data → mass/velocity → impact energy | **Composite Workflow A**: `getNearEarthAsteroids` → `calculateKineticEnergy`, agent-orchestrated, no user-scripted steps |
| Manually cross-checking flares / CMEs / geomagnetic storms for the same window | **Composite Workflow B**: `getSolarFlareData` + `getDonkiCmeData` + `getGeomagneticStormData` called together, synthesized into one correlated briefing |
| Manually assembling a daily multi-domain briefing | **Composite Workflow C**: APOD + Mars rover photos + EPIC Earth imagery combined into one digest |
| Silent data loss on a NASA rate limit / connection reset | `fetchFromUrl` retries transient failures (3 attempts, backoff) and returns an explicit `NASA_API_ERROR` marker instead of quietly returning an error page as if it were data |
| Gemini free-tier rate limits interrupting a session | Model fallback chain (`gemini-3.5-flash → gemini-3.5-flash-lite → gemini-3.1-flash-lite`) combined with multi-key rotation — both baseline and agent go through the same resilience layer |
| Disk clutter from clarification back-and-forth | Reports are only written for substantive answers; `[TOPIC: clarification_required]` turns are logged to the trajectory but not saved as reports |

### Flagship demo: Asteroid Impact Threat Assessment (`neo_plus_kinetic`)

This is the strongest evidence of real multi-step orchestration, not just API wrapping — the
agent independently executes a 2-step tool chain and cites which numbers were retrieved live vs.
derived:

```
getNearEarthAsteroids(2026-08-30)
  -> largest object identified: (2012 LE11), diameter ~1,406 m, velocity 10.07 km/s (live NASA data)
calculateKineticEnergy(2.899e11 kg, 10.07 km/s)
  -> 1.4704e19 Joules (~3,514 megatons TNT-equivalent)
```

Full trajectory: `reports/eval/*_neo_plus_kinetic_agent.md`.

## Baseline vs. Agent — how the comparison works

`EvalRunner` (triggered by typing `evals` in the running app) sends the **same fixed set of
queries** through two configurations that share everything except tool access:

```
Baseline: Gemini, zero tools, zero live data   (the "one direct prompt" baseline)
Agent:    Gemini, 10 NASA tools, live data, chat memory
```

Both go through the identical model-fallback + API-key rotation layer (`Main.callWithFallback`),
so the only experimental variable is tool access, not resilience.

**What this harness measures, honestly:** tool-selection correctness (did the agent call the
tools the task actually needs), tool usage counts, execution latency, and the generated outputs
side-by-side. **It does not automatically grade the factual/semantic correctness of the final
prose** — that still requires a human reading the transcript in `reports/eval/`. Each case now
declares its `expectedTools`, and the harness prints an automatic PASS/FAIL verdict per case based
on whether those tools were actually invoked (see `EvalRunner.evaluateToolSelection`).

Agent latency is consistently higher than baseline latency across every case in the table below —
this is an expected cost of live grounding (HTTP calls + a second LLM synthesis pass), not a
regression, and is not treated as a performance metric to optimize.

### Representative real run

| Case | Baseline behavior | Agent behavior |
|---|---|---|
| `apod_fixed_date` | Honestly declines: *"I do not have access to a live internet connection..."* | 1 tool call, returns live APOD title + explanation |
| `neo_feed` | Honestly declines | 1 tool call, returns live NEO close-approach data |
| `neo_plus_kinetic` | Honestly declines | 2 tool calls, full threat table (see flagship demo above) |
| `mars_photos` | Honestly declines | 1 tool call, live Curiosity rover photos |
| `space_weather_briefing` | Honestly declines | 3 tool calls, correlated flare/CME/storm briefing |
| `space_digest_fixed_date` | Honestly declines | 3 tool calls, combined APOD + Mars + EPIC digest |
| `exoplanets` | Honestly declines | 1 tool call — either real archive data, or (on an external network hiccup) an honest `NASA_API_ERROR` explanation instead of fabricated data |
| `ambiguous` ("show me some space photos") | Declines | Correctly asks a clarifying question instead of guessing a tool |
| `out_of_domain` (cookie recipe) | Declines (out of its stated scope) | Correctly redirects to NASA topics without calling any tool |

**Tool-selection score: 8/8 applicable cases PASS** (`ambiguous` is a judgment case with no fixed
expected tool, reported as `INFO`, not `PASS`/`FAIL` — 8 is the maximum achievable score by
design). Full `summary_*.md` and per-case transcripts are in `reports/eval/`.

The nine evaluation cases cover: simple factual retrieval, parameterized single-tool calls,
2-step tool chaining, 3-way multi-source correlation, agent autonomy on an open-ended request,
tool selection under ambiguity, and out-of-domain boundary handling.

## Improvement changelog

Derived from the project's actual commit history (`git log`), each entry links what changed to
why:

| Stage | What changed | Why |
|---|---|---|
| First edition | Baseline 10-tool NASA agent, single Gemini key, no resilience | Get core tool-calling working end to end |
| 3 API Keys | Added multi-key rotation | Free-tier Gemini quota was exhausted mid-session during testing |
| Second edition | Refined tool set and prompt | Iterating on tool descriptions and response formatting |
| uplink failure message | Added a user-facing fallback message when all keys/models are exhausted | Silent stack traces are not an acceptable failure mode for an operator-facing tool |
| uplink failure message without saved file | Stopped writing a report file for failure/clarification turns | Avoid cluttering `reports/` with non-substantive outputs |
| log trajectory | Added `agent_trajectory.log` | Needed an audit trail of what the agent actually did, not just its final answer |
| Third edition | Added `BaselineAgent`, `EvalRunner`, multi-model fallback chain, `NASA_API_KEY` env var, composite workflows A/B/C | Needed a fair, reproducible baseline-vs-agent comparison and stronger multi-tool orchestration evidence for judging |
| Final edition (this version) | Fixed `getExoplanetArchive`/`getTechPortProjects` to actually honor their declared parameters instead of ignoring them; `fetchFromUrl` now returns an explicit `NASA_API_ERROR` marker instead of silently passing an HTTP error body off as data; added a grounding rule so the agent can no longer claim "retrieved from NASA" without an actual tool call in that turn; made eval dates fixed instead of "today" for reproducibility; added automatic tool-selection PASS/FAIL scoring | An external code review caught that two tools had a description/behavior mismatch, and testing caught the agent fabricating an "exoplanet archive" answer with zero tool calls on one run — both are credibility risks for a judged submission and needed fixing before submission, not after |
| Post-review hardening | Broadened key rotation to also trigger on `403 PERMISSION_DENIED` (not just 429/503/quota) — different free-tier API keys can have access to different models, so a per-key access error is often worth retrying with the next key, not failing outright. Fixed a URL-building bug in `getExoplanetArchive` where an unescaped `<` character (`rowid<=10`) made `URI.create()` reject the request on every call with no discovery-method filter — the whole ADQL query is now URL-encoded in one pass instead of hand-splicing raw and encoded fragments. Added connect/request timeouts, a `User-Agent` header, and full (untruncated) network-exception logging to the console | Live evaluation runs surfaced both bugs directly: some queries were failing with `agent_tools=0` purely because one bad API key killed the whole request instead of rotating past it, and `exoplanets` was failing 100% of the time with a truncated, undiagnosable error message. Tool-selection score went from 6/8 to the current 8/8 after both fixes |

## Hot take / lesson learned

The single most dangerous failure mode we found wasn't a crash — it was the agent **fabricating
a confident, well-formatted answer and citing NASA as the source when it had not actually called
any tool.** Tool access does not guarantee tool *use*; an LLM with both training knowledge and a
matching tool available will sometimes silently answer from memory instead, and the output looks
identical in tone and formatting whether it's grounded or not. Fixing this required an explicit
grounding rule in the system prompt ("never present facts as retrieved unless a tool call for
that exact request actually happened") plus per-case `expectedTools` checks in the eval harness —
you cannot catch this class of bug by reading the final answer; you have to check the trajectory.
If we built this again, we'd add trajectory-based grounding checks from day one instead of
retrofitting them after finding the bug in an eval transcript.

## Reproduction guide

**Requirements:** Java 17+, Maven, a free NASA API key (https://api.nasa.gov), one or more free
Gemini API keys (https://aistudio.google.com/apikey).

```bash
export NASA_API_KEY=your_nasa_key
export GEMINI_API_KEYS=key1,key2,key3   # comma-separated; one is enough, more adds resilience

mvn clean package
java -jar target/micro1-agent-1.0-SNAPSHOT.jar
```

Inside the running app:
- Type any natural-language NASA question to talk to the agent directly (e.g. `Give me a space
  weather risk briefing for 2026-08-20 to 2026-08-27`).
- Type `evals` to run the full baseline-vs-agent comparison — writes `reports/eval/summary_*.md`
  (comparison table + PASS/FAIL scores) and one full transcript per case/variant.
- Type `exit` to quit.

**Expected runtime/cost:** the full 9-case eval run takes roughly 2-5 minutes and consumes on the
order of 20-40 Gemini API calls total (baseline + agent, including any model/key fallback
retries) — free-tier quota is sufficient for one full run. NASA API calls are free but subject to
your `NASA_API_KEY`'s hourly rate limit.

**Known limitation:** the evaluation harness checks tool-selection correctness automatically, but
does not grade the factual accuracy of the generated prose — read the transcripts in
`reports/eval/` for that.

Demo Video:
https://youtu.be/v_EfBnrCQ-s


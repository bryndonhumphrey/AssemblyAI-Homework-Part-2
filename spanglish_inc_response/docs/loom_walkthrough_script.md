# Loom Walkthrough Script

Length: 4-6 minutes

## 1. Open With Customer Impact

"Spanglish Inc. is live in production on async and is evaluating Streaming for English/Spanish legal note-taking. They reported that Streaming does not work at all, so I started by validating their code against the current Streaming v3 and Universal-3 Pro docs."

## 2. Show The Root Cause

"The WebSocket URL is the core issue. It omits `speech_model`, which is required, and it declares `encoding=opus`. The Java microphone code is actually sending signed 16-bit little-endian PCM bytes. Universal-3 Pro accepts `pcm_s16le` or `pcm_mulaw`, so this connection was negotiated incorrectly. The sample also used 25ms chunks, while the API reference expects 50ms to 1000ms binary chunks."

## 3. Show The Fixed Code

"The fixed sample uses `speech_model=u3-rt-pro`, `encoding=pcm_s16le`, 50ms audio chunks, and a prompt that gives legal/interpreter context. It also uses `end_of_turn` to detect final turns, and makes local WAV recording opt-in so the sample does not accidentally retain customer audio."

## 4. Explain Why This Is Not A Product Bug

"These are integration mismatches before the model is meaningfully transcribing: required model parameter missing, unsupported encoding, wrong chunk duration, and a class-name compile issue in the pasted Java. Universal-3 Pro is the intended model for English/Spanish code switching."

## 5. Scaling Plan

"For 2,000 concurrent streams, the main constraint is not total concurrency, but new sessions per minute. Paid accounts start at 100+ new sessions per minute and auto-scale by 10% when usage is at least 70%. For an immediate launch, we should confirm or raise their account limit ahead of time. They should rate-limit stream creation, back off on close code 1008, and terminate sessions explicitly."

## 6. Privacy Answer

"For Streaming STT, AssemblyAI offers zero data retention for audio and transcripts when the account is opted out of model training. Metadata remains for logging and billing. For async, retention is a separate policy, so we should verify their TTL/BAA/delete settings separately."

## 7. Close With Next Steps

"Send the corrected sample and customer email, confirm model-training opt-out and current rate limit, then offer a live implementation check with their production-like audio path."

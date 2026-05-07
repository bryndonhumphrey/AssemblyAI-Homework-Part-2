# OOO Handoff: Spanglish Inc. Streaming Escalation

## Current State

Spanglish Inc. reported that Streaming STT "doesn't work at all" and sent a Java snippet. I reviewed the snippet against current AssemblyAI Streaming v3 and Universal-3 Pro docs. The issue appears to be their integration, not a product bug.

The corrected sample is in:

`spanglish_inc_response/code/src/main/java/com/assemblyai/Spanglish.java`

Customer-facing draft:

`spanglish_inc_response/docs/customer_email.md`

## Main Root Cause

Their WebSocket URL omitted `speech_model` and declared `encoding=opus`, while their Java code sends raw PCM16 little-endian microphone bytes. Universal-3 Pro Streaming should use:

```text
speech_model=u3-rt-pro
sample_rate=16000
encoding=pcm_s16le
```

They were also sending 25ms audio chunks. The fixed sample sends 50ms chunks.

## Customer Guidance To Send

- Use Universal-3 Pro Streaming for English/Spanish code switching.
- Use `prompt` for courtroom/interpreter context.
- Use `language_detection=true` only if they want language metadata.
- Do not use `language_code` for Universal-3 Pro Streaming.
- Disable local audio retention in their app unless they intentionally need it. The fixed sample makes WAV recording opt-in.
- Gracefully terminate every session with `{"type":"Terminate"}`.

## Scaling To 2,000 Streams

AssemblyAI Streaming uses new-sessions-per-minute limits rather than a hard concurrent-stream cap. Paid accounts start at 100+ new sessions/minute, auto-scale by 10% when utilization is at least 70%, and custom concurrency limits are available.

Action items:

- Confirm their current rate limit in the dashboard or with Support/Sales.
- If they need an immediate hard launch, request a limit increase instead of relying only on organic ramp.
- If starting at 100 new sessions/minute, a controlled max-rate ramp reaches roughly 2,000 open sessions in about 12 minutes.
- Tell them to implement a rate limiter and backoff on WebSocket close code `1008`.
- If their backend proxies audio, validate network egress for about 64 MB/s before WebSocket overhead at 2,000 streams.

## Privacy Items

- Confirm whether Spanglish is opted out of model training.
- For Streaming STT, AssemblyAI offers zero data retention of audio and transcripts when opted out. Metadata remains for logging and billing.
- If they need data residency, use `streaming.us.assemblyai.com` or `streaming.eu.assemblyai.com`.
- If they continue async usage, do not conflate async retention with streaming retention. Async artifacts follow TTL/BAA/delete-request behavior.

## Suggested Next Customer Touch

1. Send the customer email draft and corrected code.
2. Offer a 30-minute working session to run the sample with their API key and actual audio path.
3. Ask for timestamps/session IDs/close codes from their failed production attempts if they still see failures after the code change.
4. Open an account-side request to confirm rate limit and ZDR/model-training opt-out status.

## Sources

- Universal-3 Pro Streaming: https://www.assemblyai.com/docs/streaming/universal-3-pro
- Universal-3 Pro Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming
- Streaming concurrency: https://www.assemblyai.com/docs/streaming/concurrency
- Data retention and model training: https://www.assemblyai.com/docs/data-retention-and-model-training

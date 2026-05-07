# Draft Customer Email

Subject: Fix for your Streaming STT issue and plan for 2,000 concurrent streams

Hi Spanglish team,

We reviewed the Java snippet you sent and found the issue. This does not appear to be a failure in AssemblyAI's Streaming STT service. The sample was opening the Streaming v3 WebSocket with parameters that did not match the audio being sent.

What went wrong:

- The request did not include `speech_model`. Streaming v3 requires `speech_model` on every transcription request. For your use case, the right model is Universal-3 Pro Streaming: `speech_model=u3-rt-pro`.
- The request declared `encoding=opus`, but the Java microphone capture code sends raw signed 16-bit little-endian PCM audio. Streaming v3 accepts `pcm_s16le` or `pcm_mulaw`, so this should be `encoding=pcm_s16le`.
- The sample was sending 25ms chunks. The Streaming API expects binary audio chunks between 50ms and 1000ms, so the fixed code sends 50ms chunks.
- The pasted Java file also instantiated `StreamingTranscription`, but the class name in the file was `Spanglish`. If that is in the production code too, it would fail before opening a stream.

We prepared a corrected Java sample in `code/src/main/java/com/assemblyai/Spanglish.java`. The important connection parameters are:

```text
speech_model=u3-rt-pro
sample_rate=16000
encoding=pcm_s16le
language_detection=true
```

Universal-3 Pro Streaming supports native code switching across English and Spanish, so you do not need to split streams by language. We also added a domain prompt for court and interpreter scenarios to give the model useful context.

## Scaling To 2,000 Concurrent Streams

AssemblyAI Streaming STT does not have a hard cap on total concurrent streaming sessions for paid accounts. The operational limit is the rate of new sessions opened per minute. Paid accounts start at 100+ new sessions per minute, and the limit automatically increases by 10% whenever you use at least 70% of your current limit.

For an immediate production ramp to 2,000 concurrent streams, we recommend:

1. Ask us to confirm or raise your account's new-sessions-per-minute limit before launch. AssemblyAI offers custom concurrency limits for higher-volume workloads.
2. Open streams with a rate limiter instead of a burst. If starting from a 100 new-sessions/minute limit and holding streams open, opening at the allowed rate reaches about 2,000 active streams in roughly 12 minutes through automatic scaling.
3. Treat each audio stream as one WebSocket session. Do not multiplex multiple conversations into one WebSocket.
4. Gracefully terminate every session by sending `{"type":"Terminate"}` when the conversation ends. Otherwise, sessions can remain open until the maximum session duration and still count toward concurrency and billing.
5. Handle WebSocket close code `1008` with message `Unauthorized connection: Too many concurrent sessions` as a rate-limit signal. Back off and retry instead of reconnecting in a tight loop.
6. Use temporary streaming tokens if browser or device clients connect directly to AssemblyAI. Generate these tokens on your server so your API key is never exposed client-side.
7. For strict data residency, use `wss://streaming.us.assemblyai.com/v3/ws` or `wss://streaming.eu.assemblyai.com/v3/ws`. For lowest latency, use the default edge-routed endpoint.

One sizing note: 16kHz mono PCM16 audio is roughly 32 KB/s per stream before WebSocket overhead. If your backend proxies all audio to AssemblyAI, 2,000 streams is roughly 64 MB/s, or about 512 Mbps, of outbound audio traffic before overhead. If clients connect directly with temporary tokens, that traffic does not need to pass through your backend.

## Privacy And Retention

For Streaming STT, AssemblyAI offers zero data retention for audio and transcripts when the account is opted out of model training. Certain metadata is still retained for logging and billing. We recommend confirming that your production account is opted out before the streaming rollout.

We also changed the sample so it does not save a local WAV by default. The original snippet retained customer audio locally in memory and then wrote it to disk. The fixed sample only does that when `SAVE_LOCAL_WAV=true`, which should be limited to explicit debugging scenarios.

References:

- Universal-3 Pro Streaming: https://www.assemblyai.com/docs/streaming/universal-3-pro
- Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming
- Streaming concurrency: https://www.assemblyai.com/docs/streaming/concurrency
- Temporary streaming tokens: https://www.assemblyai.com/docs/streaming/authenticate-with-a-temporary-token
- Data retention and model training: https://www.assemblyai.com/docs/data-retention-and-model-training

Best,

Bryndon Humphrey

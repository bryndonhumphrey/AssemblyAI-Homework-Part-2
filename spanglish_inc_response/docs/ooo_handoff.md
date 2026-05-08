# OOO Handoff: Spanglish Inc. Streaming Escalation

## Current State

Spanglish Inc. reported that Streaming STT "doesn't work at all" and sent a Java snippet. I reviewed the snippet against current AssemblyAI Streaming v3 and Universal-3 Pro docs. The issue appears to be their integration, not a product bug.

The corrected sample is available here:
https://github.com/bryndonhumphrey/AssemblyAI-Homework-Part-2/blob/main/spanglish_inc_response/code/src/main/java/com/assemblyai/Spanglish.java

Customer-facing email that was sent:
https://github.com/bryndonhumphrey/AssemblyAI-Homework-Part-2/blob/main/spanglish_inc_response/docs/customer_email.md

## High-level Summary

Their WebSocket URL omitted `speech_model` and declared `encoding=opus`, while their Java code sends raw PCM16 little-endian microphone bytes. Universal-3 Pro Streaming should use:

```text
speech_model=u3-rt-pro
sample_rate=16000
encoding=pcm_s16le
```

They were also sending 25ms audio chunks. The fixed sample sends 50ms chunks.

## Customer Guidance Sent

- The request did not include `speech_model`. Streaming v3 requires `speech_model` on every transcription request. For your use case, the right model is Universal-3 Pro Streaming: `speech_model=u3-rt-pro`.
- The request declared `encoding=opus`, but the Java microphone capture code sends raw signed 16-bit little-endian PCM audio. Streaming v3 accepts `pcm_s16le` or `pcm_mulaw`, so this should be `encoding=pcm_s16le`.
- The sample was sending 25ms chunks. The Streaming API expects binary audio chunks between 50ms and 1000ms, so the fixed code sends 50ms chunks.
- There was a Compile-level issue in the original code snippet. The class is `Spanglish`, but `main` instantiates `StreamingTranscription`. If that is in the production code too, it would fail before opening a stream.


## Scaling To 2,000 Streams

The customer email outlines how they can use auto-scaling to scale up to 2000 concurrent streams over a 12 minute period.

## Privacy Items

Spanglish wants confidence that no customer data is being retained by our systems. Sent them our data retention policy and outlined how to opt-out of data collection.

## Suggested Next Customer Touch

1. Follow up with customer to ensure the fixes resolved their issues in production.
2. Ensure they followed data Opting Out Process.

## Sources

- Universal-3 Pro Streaming: https://www.assemblyai.com/docs/streaming/universal-3-pro
- Universal-3 Pro Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming
- Streaming concurrency: https://www.assemblyai.com/docs/streaming/concurrency
- Data retention and model training: https://www.assemblyai.com/docs/data-retention-and-model-training

Please let me know if you have any questions.
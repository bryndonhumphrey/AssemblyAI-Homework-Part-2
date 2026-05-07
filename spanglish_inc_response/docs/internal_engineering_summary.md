# Internal Engineering Summary

## Conclusion

This is very likely a customer integration issue, not an AssemblyAI Streaming STT product bug.

## Evidence

The customer snippet violates multiple current Streaming v3 requirements:

- Missing required `speech_model`. Current docs state every streaming transcription request must include `speech_model`; Universal-3 Pro should be requested with `u3-rt-pro`.
- Invalid/mismatched encoding. The URL declares `encoding=opus`, but the Java Sound configuration captures signed 16-bit little-endian PCM bytes. Universal-3 Pro Streaming accepts `pcm_s16le` or `pcm_mulaw`, not Opus.
- Too-small audio chunks. The code sends 400 frames at 16kHz, which is 25ms. The API reference says binary audio chunks should contain 50ms to 1000ms of audio.
- Compile-level issue in the pasted snippet. The class is `Spanglish`, but `main` instantiates `StreamingTranscription`.

Universal-3 Pro Streaming itself is a good fit for the customer because it supports English and Spanish code switching natively. `language_detection=true` can return language metadata on `Turn` events, but language selection should not be attempted with `language_code`; for Universal-3 Pro, domain/language context should be supplied with `prompt`.

## Expected Customer Symptoms

Depending on exactly what made it into production, they may see:

- Immediate compile failure from the class-name mismatch.
- WebSocket handshake close/failure due to missing required `speech_model` or unsupported `encoding`.
- Open connection with no useful transcript or inconsistent turns due to audio format/chunk mismatch.
- Confusion around final output handling because Universal-3 Pro should use `end_of_turn` as the final-turn signal.

## Product Follow-Ups

- No core Streaming STT bug indicated by the sample.
- Consider improving Java examples in docs or adding a Java quickstart for Universal-3 Pro Streaming.
- Consider surfacing especially explicit close reasons for unsupported encodings and missing required `speech_model`, if not already present in server logs.
- Account team should confirm Spanglish's current new-sessions-per-minute limit and model-training opt-out status before their 2,000-stream ramp.

## References

- Streaming quickstart: https://www.assemblyai.com/docs/streaming/getting-started/transcribe-streaming-audio
- Universal-3 Pro Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming
- Universal-3 Pro language guidance: https://www.assemblyai.com/docs/streaming/universal-3-pro
- Streaming concurrency: https://www.assemblyai.com/docs/streaming/concurrency

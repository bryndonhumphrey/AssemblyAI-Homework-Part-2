# Privacy And Retention Answers

## Short Answer For Spanglish Inc.

For Streaming STT, AssemblyAI offers zero data retention of audio and transcripts when the account is opted out of model training. Certain metadata is still retained for logging and billing.

For their existing asynchronous production use case, retention is different from Streaming. Async artifacts can be controlled through account TTL settings, BAA defaults, and deletion requests. We should not imply async has the same zero-retention behavior as Streaming unless their account settings and contract explicitly provide that.

## Recommended Customer Commitments

- Confirm whether Spanglish Inc. is already opted out of model training.
- If not, start the opt-out path before their streaming production launch.
- Confirm whether they require US-only or EU-only routing. If yes, use a data-zone endpoint instead of the edge-routed default.
- Confirm whether they need a BAA or already have one executed.
- Advise them not to store local audio by default. The fixed sample disables local WAV output unless `SAVE_LOCAL_WAV=true`.
- If browser clients connect directly, use temporary streaming tokens generated server-side so the API key is never sent to the browser or device.

## Details

Streaming:

- Audio and transcript retention can be zero when opted out of model training.
- Logging and billing metadata remains.
- Streaming connections use TLS 1.3 according to AssemblyAI's trust and security docs.
- Data-zone endpoints are available for US or EU residency requirements.

Async:

- Uploaded audio and transcription artifacts follow the async retention rules documented by AssemblyAI.
- Final transcription artifacts can be deleted by customer request, and TTL can be configured as low as one hour where applicable.
- The deletion mechanism for async final artifacts depends on AWS TTL processing, so deletion starts at TTL expiration but completion can vary.

## References

- Data retention and model training: https://www.assemblyai.com/docs/data-retention-and-model-training
- Streaming endpoints and data zones: https://www.assemblyai.com/docs/streaming/endpoints-and-data-zones
- Temporary streaming tokens: https://www.assemblyai.com/docs/streaming/authenticate-with-a-temporary-token

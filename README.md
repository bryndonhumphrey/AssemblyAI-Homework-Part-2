# Spanglish Inc. Streaming STT Escalation Package

This package contains the shareable materials for the Spanglish Inc. critical issue.

## Contents

- `code/` - corrected Java sample using AssemblyAI Streaming STT v3 and Universal-3 Pro Streaming.
- `docs/customer_email.md` - customer-facing explanation and scaling plan.
- `docs/privacy_retention_answers.md` - direct answers to data privacy and retention concerns.
- `docs/internal_engineering_summary.md` - internal engineering summary and why this is not an AssemblyAI product bug.
- `docs/ooo_handoff.md` - handoff notes for the returning Applied AI Engineer.
- `docs/loom_walkthrough_script.md` - short script for recording a Loom-style walkthrough.

## Run The Sample

Prerequisites:

- Java 17 or newer
- Maven

On macOS with Homebrew:

```bash
brew install openjdk@17 maven
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
```

Verify both tools are available:

```bash
java -version
mvn -version
```

```bash
cd spanglish_inc_response/code
export ASSEMBLYAI_API_KEY="your_api_key"
mvn compile exec:java
```

Optional data residency:

```bash
export ASSEMBLYAI_STREAMING_HOST="streaming.us.assemblyai.com"
```

Optional local WAV recording for debugging only:

```bash
export SAVE_LOCAL_WAV=true
```

## Primary References

- AssemblyAI Streaming quickstart: https://www.assemblyai.com/docs/streaming/getting-started/transcribe-streaming-audio
- Universal-3 Pro Streaming: https://www.assemblyai.com/docs/streaming/universal-3-pro
- Universal-3 Pro Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming
- Streaming concurrency: https://www.assemblyai.com/docs/streaming/concurrency
- Streaming endpoints and data zones: https://www.assemblyai.com/docs/streaming/endpoints-and-data-zones
- Data retention and model training: https://www.assemblyai.com/docs/data-retention-and-model-training
- Temporary streaming tokens: https://www.assemblyai.com/docs/streaming/authenticate-with-a-temporary-token

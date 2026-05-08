# Customer Email

Subject: Hotfix for your Java Streaming STT issue and plan for 2,000 concurrent streams

Hi Spanglish team,

I reviewed the Java snippet you sent and was able to make some modifications to get it working. Luckily, this does not appear to be a failure in AssemblyAI's Streaming STT service. The original code was opening the Streaming v3 WebSocket with parameters that did not match the audio being sent.
Video Demo of working code: https://drive.google.com/file/d/1vmCoXSia3BNWUBFoV4O3uacbOwNKkbPh/view?usp=sharing

I prepared a corrected Java sample available at: https://github.com/bryndonhumphrey/AssemblyAI-Homework-Part-2/blob/main/spanglish_inc_response/code/src/main/java/com/assemblyai/Spanglish.java
Here are the instructions to getting it working: https://github.com/bryndonhumphrey/AssemblyAI-Homework-Part-2/blob/main/README.md

Important connection parameters are:

```text
speech_model=u3-rt-pro
sample_rate=16000
encoding=pcm_s16le
language_detection=true
```

Universal-3 Pro Streaming supports native code switching across English and Spanish, which removes the need to split streams by language. We also added a domain prompt to give the model useful processing context.

Observations about the original code:
- The request did not include `speech_model`. Streaming v3 requires `speech_model` on every transcription request. For your use case, the right model is Universal-3 Pro Streaming: `speech_model=u3-rt-pro`.
- The request declared `encoding=opus`, but the Java microphone capture code sends raw signed 16-bit little-endian PCM audio. Streaming v3 accepts `pcm_s16le` or `pcm_mulaw`, so this should be `encoding=pcm_s16le`.
- The code was originally sending 25ms chunks. The Streaming API expects binary audio chunks between 50ms and 1000ms, so the fixed code sends 50ms chunks.
- There was a compile-level issue in the original code snippet. The class is `Spanglish`, but `main` instantiates `StreamingTranscription`. If that is in the production code too, it would fail before opening a stream.

More info on Universal-3 Pro Streaming: https://www.assemblyai.com/docs/streaming/universal-3-pro
Universal-3 Pro Streaming API reference: https://www.assemblyai.com/docs/api-reference/streaming-api/universal-3-pro-streaming/universal-3-pro-streaming

## Scaling To 2,000 Concurrent Streams

We can definitely support 2,000 concurrent streams, AssemblyAI Streaming STT does not have a hard cap on total concurrent streaming sessions for paid accounts. The operational limit is the rate of new sessions opened per minute. Paid accounts start at 100+ new sessions per minute, and the limit automatically increases by 10% each minute whenever you use at least 70% of your current limit.

For an immediate production ramp to 2,000 concurrent streams using our auto-scaling feature, assuming you max out the new-stream limit every minute, we recommend starting all of your streams over a 12 minute period using the table below. (github may have issues rendering the table below in preview view, switch to code view)

| Minute | New sessions/min | Total concurrent streams |
| 1 | 100 | 100 |
| 2 | 110 | 210 |
| 3 | 121 | 331 |
| 4 | 133 | 464 |
| 5 | 146 | 610 |
| 6 | 160 | 770 |
| 7 | 176 | 946 |
| 8 | 193 | 1,139 |
| 9 | 212 | 1,351 |
| 10 | 233 | 1,584 |
| 11 | 256 | 1,840 |
| 12 | 281 | 2,121 |

More info on concurrency & auto-scaling: https://www.assemblyai.com/docs/streaming/concurrency#auto-scaling

## Privacy And Retention

For Streaming STT, AssemblyAI offers zero data retention for audio and transcripts when the account is opted out of model training. Certain metadata is still retained for logging and billing. More info: https://www.assemblyai.com/docs/data-retention-and-model-training#streaming-production-environment

We recommend confirming that your account is opted out by following the Opting Out Process:
- Send an email to data-opt-out@assemblyai.com
- Ensure you send the email from the address associated with your AssemblyAI account
- In the email, clearly state your request to opt out of data sharing for model training
- Once we receive and process your request, we will respond with an email confirmation of your opt-out status. Your opt-out status will be effective from the time of that confirmation going forward

Related to ensuring additional privacy in your code:
- We changed the code so it does not save a local WAV by default. The original snippet retained customer audio locally in memory and then wrote it to disk. The fixed code only does that when `SAVE_LOCAL_WAV=true`, which should be limited to explicit debugging scenarios.
- If browser clients connect directly, we recommend using temporary streaming tokens generated server-side so the API key is never sent to the browser or device.

More info on data opt-out: https://www.assemblyai.com/docs/faq/how-to-opt-out-of-data-sharing-for-our-model-improvement-program
Full data retention policy: https://www.assemblyai.com/docs/data-retention-and-model-training
More info on streaming endpoints and data zones: https://www.assemblyai.com/docs/streaming/endpoints-and-data-zones
More info on temporary streaming tokens: https://www.assemblyai.com/docs/streaming/authenticate-with-a-temporary-token

Please let me know if you have any questions.

Best,
Bryndon Humphrey

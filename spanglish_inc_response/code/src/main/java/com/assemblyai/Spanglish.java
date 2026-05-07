package com.assemblyai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Spanglish {
    private static final String API_KEY_ENV = "ASSEMBLYAI_API_KEY";

    /*
     * Use "streaming.assemblyai.com" for edge routing, or set
     * ASSEMBLYAI_STREAMING_HOST to "streaming.us.assemblyai.com" or
     * "streaming.eu.assemblyai.com" when the customer needs strict data residency.
     */
    private static final String API_HOST = System.getenv().getOrDefault(
            "ASSEMBLYAI_STREAMING_HOST",
            "streaming.assemblyai.com"
    );

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int BYTES_PER_SAMPLE = SAMPLE_SIZE_IN_BITS / 8;

    /*
     * AssemblyAI expects each binary audio message to contain 50ms to 1000ms of
     * audio. The customer's sample used 400 frames (25ms), so use 800 frames
     * instead: 0.050s * 16000Hz = 800 samples.
     */
    private static final int FRAMES_PER_BUFFER = 800;

    /*
     * The original sample always retained audio in memory and wrote a local WAV.
     * Make that opt-in for production privacy hygiene.
     */
    private static final boolean SAVE_LOCAL_WAV = Boolean.parseBoolean(
            System.getenv().getOrDefault("SAVE_LOCAL_WAV", "false")
    );
    private static final boolean LANGUAGE_DETECTION = Boolean.parseBoolean(
            System.getenv().getOrDefault("LANGUAGE_DETECTION", "true")
    );

    private final String apiKey;
    private final URI apiEndpoint;
    private TargetDataLine microphone;
    private final List<byte[]> recordedFrames = new ArrayList<>();
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean cleanupStarted = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final Gson gson = new Gson();
    private AssemblyAIWebSocketClient wsClient;
    private Thread audioThread;

    public Spanglish() {
        this.apiKey = readApiKey();
        this.apiEndpoint = buildApiEndpoint();
    }

    public static void main(String[] args) {
        /*
         * The customer snippet instantiated StreamingTranscription, but the class
         * in the file is Spanglish. That prevents the sample from compiling as
         * pasted. Instantiate this class directly.
         */
        new Spanglish().run();
    }

    public void run() {
        System.out.println("Starting AssemblyAI Universal-3 Pro streaming transcription...");
        if (SAVE_LOCAL_WAV) {
            System.out.println("SAVE_LOCAL_WAV=true, so audio will be saved when the session ends.");
        }

        try {
            initializeMicrophone();
            connectWebSocket();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nCtrl+C received. Stopping...");
                stopRequested.set(true);
                cleanup();
                shutdownLatch.countDown();
            }));

            System.out.println("Speak into your microphone. Press Ctrl+C to stop.");
            shutdownLatch.await();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private static String readApiKey() {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set " + API_KEY_ENV + " before running this sample.");
        }
        return apiKey;
    }

    private static URI buildApiEndpoint() {
        Map<String, String> params = new LinkedHashMap<>();

        /*
         * speech_model is required for every v3 Streaming STT session. There is no
         * default model. Use Universal-3 Pro for native English/Spanish code
         * switching.
         */
        params.put("speech_model", "u3-rt-pro");
        params.put("sample_rate", Integer.toString(SAMPLE_RATE));

        /*
         * Java Sound is configured below for signed 16-bit little-endian PCM.
         * The original sample sent those raw PCM bytes while declaring
         * encoding=opus. Opus is not a supported v3 streaming encoding, and the
         * negotiated encoding must match the bytes sent on the socket.
         */
        params.put("encoding", "pcm_s16le");

        /*
         * language_detection adds language metadata to Turn events. It does not
         * translate text. Universal-3 Pro code-switches natively; the prompt gives
         * the model domain context and explicitly tells it to preserve the source
         * language rather than translating English phrases into Spanish.
         */
        params.put("language_detection", Boolean.toString(LANGUAGE_DETECTION));
        params.put("prompt", System.getenv().getOrDefault("ASSEMBLYAI_STREAMING_PROMPT", String.join(" ",
                "Transcribe the audio verbatim in the original language spoken.",
                "Do not translate between English and Spanish.",
                "If the speaker says an English phrase, output English words.",
                "If the speaker says a Spanish phrase, output Spanish words.",
                "Preserve code-switching exactly as spoken.",
                "Use standard punctuation.",
                "Include filler words and incomplete utterances when spoken.",
                "Context: legal or court proceedings may include an interpreter;",
                "preserve names, legal terms, dates, numbers, and case identifiers."
        )));

        /*
         * Universal-3 Pro uses punctuation-based turn detection. These values keep
         * the default fast behavior while making the setting explicit for the
         * customer.
         */
        params.put("min_turn_silence", "100");
        params.put("max_turn_silence", "1000");

        String query = encodeQuery(params);
        return URI.create("wss://" + API_HOST + "/v3/ws?" + query);
    }

    private static String encodeQuery(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append("&");
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append("=");
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private void initializeMicrophone() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_IN_BITS,
                CHANNELS,
                CHANNELS * BYTES_PER_SAMPLE,
                SAMPLE_RATE,
                false
        );

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Microphone does not support 16kHz mono PCM16 little-endian audio.");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format, FRAMES_PER_BUFFER * BYTES_PER_SAMPLE * 4);
        System.out.println("Microphone initialized successfully.");
    }

    private void connectWebSocket() throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", apiKey);
        wsClient = new AssemblyAIWebSocketClient(apiEndpoint, headers);
        if (!wsClient.connectBlocking()) {
            throw new IOException("WebSocket connection did not complete.");
        }
    }

    private void startAudioStreaming() {
        isRecording.set(true);
        microphone.start();
        audioThread = new Thread(() -> {
            System.out.println("Starting audio streaming...");
            byte[] buffer = new byte[FRAMES_PER_BUFFER * BYTES_PER_SAMPLE];

            while (!stopRequested.get() && isRecording.get()) {
                try {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead <= 0) {
                        continue;
                    }

                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                    if (SAVE_LOCAL_WAV) {
                        synchronized (recordedFrames) {
                            recordedFrames.add(audioData);
                        }
                    }

                    if (wsClient != null && wsClient.isOpen()) {
                        wsClient.send(audioData);
                    }
                } catch (Exception e) {
                    if (!stopRequested.get()) {
                        System.err.println("Error streaming audio: " + e.getMessage());
                    }
                    break;
                }
            }
            System.out.println("Audio streaming stopped.");
        }, "assemblyai-audio-stream");
        audioThread.start();
    }

    private void cleanup() {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }

        stopRequested.set(true);
        isRecording.set(false);

        if (microphone != null) {
            if (microphone.isActive()) {
                microphone.stop();
            }
            microphone.close();
        }

        if (audioThread != null && audioThread.isAlive()) {
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (wsClient != null && wsClient.isOpen()) {
            try {
                JsonObject terminateMsg = new JsonObject();
                terminateMsg.addProperty("type", "Terminate");
                wsClient.send(gson.toJson(terminateMsg));

                /*
                 * Give the service time to return the Termination event before
                 * closing the TCP connection. Production clients should wait for
                 * the Termination event or enforce a bounded timeout.
                 */
                Thread.sleep(2000);
                wsClient.closeBlocking();
            } catch (Exception e) {
                System.err.println("Error closing WebSocket: " + e.getMessage());
            }
        }

        if (SAVE_LOCAL_WAV) {
            saveWavFile();
        }
        shutdownLatch.countDown();
        System.out.println("Cleanup complete. Exiting.");
    }

    private void saveWavFile() {
        if (recordedFrames.isEmpty()) {
            System.out.println("No audio data recorded.");
            return;
        }

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String filename = "recorded_audio_" + timestamp + ".wav";

        try {
            int totalDataSize = 0;
            synchronized (recordedFrames) {
                for (byte[] frame : recordedFrames) {
                    totalDataSize += frame.length;
                }
            }

            File wavFile = new File(filename);
            try (FileOutputStream fos = new FileOutputStream(wavFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                writeWavHeader(bos, totalDataSize);

                synchronized (recordedFrames) {
                    for (byte[] frame : recordedFrames) {
                        bos.write(frame);
                    }
                }
            }

            double durationSeconds = (double) totalDataSize / (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE);
            System.out.printf("Audio saved to: %s%n", filename);
            System.out.printf("Duration: %.2f seconds%n", durationSeconds);
        } catch (IOException e) {
            System.err.println("Error saving WAV file: " + e.getMessage());
        }
    }

    private void writeWavHeader(OutputStream out, int dataSize) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(44);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) CHANNELS);
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE);
        buffer.putShort((short) (CHANNELS * BYTES_PER_SAMPLE));
        buffer.putShort((short) SAMPLE_SIZE_IN_BITS);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        out.write(buffer.array());
    }

    private class AssemblyAIWebSocketClient extends WebSocketClient {
        AssemblyAIWebSocketClient(URI serverUri, Map<String, String> headers) {
            super(serverUri, headers);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println("WebSocket connection opened.");
            System.out.println("Connected to: " + apiEndpoint);
            startAudioStreaming();
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonObject data = gson.fromJson(message, JsonObject.class);
                String msgType = data.has("type") ? data.get("type").getAsString() : "";

                switch (msgType) {
                    case "Begin" -> handleBeginMessage(data);
                    case "SpeechStarted" -> {
                        // Universal-3 Pro may send this before Turn messages.
                    }
                    case "Turn" -> handleTurnMessage(data);
                    case "Termination" -> handleTerminationMessage(data);
                    default -> {
                        // Ignore unknown message types so new non-breaking events do not crash the client.
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling message: " + e.getMessage());
            }
        }

        private void handleBeginMessage(JsonObject data) {
            String sessionId = data.has("id") ? data.get("id").getAsString() : "unknown";
            long expiresAt = data.has("expires_at") ? data.get("expires_at").getAsLong() : 0L;
            String formattedTime = expiresAt == 0L
                    ? "unknown"
                    : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochSecond(expiresAt));

            System.out.printf("%nSession began: ID=%s, ExpiresAt=%s%n", sessionId, formattedTime);
        }

        private void handleTurnMessage(JsonObject data) {
            String transcript = data.has("transcript") ? data.get("transcript").getAsString() : "";
            boolean endOfTurn = data.has("end_of_turn") && data.get("end_of_turn").getAsBoolean();
            String language = data.has("language_code") ? data.get("language_code").getAsString() : "";

            if (endOfTurn) {
                System.out.print("\r" + " ".repeat(100) + "\r");
                if (language.isBlank()) {
                    System.out.println(transcript);
                } else {
                    System.out.printf("[%s] %s%n", language, transcript);
                }
            } else {
                System.out.print("\r" + transcript);
            }
        }

        private void handleTerminationMessage(JsonObject data) {
            double audioDuration = data.has("audio_duration_seconds")
                    ? data.get("audio_duration_seconds").getAsDouble()
                    : 0.0;
            double sessionDuration = data.has("session_duration_seconds")
                    ? data.get("session_duration_seconds").getAsDouble()
                    : 0.0;

            System.out.printf(
                    "%nSession terminated: Audio Duration=%.2fs, Session Duration=%.2fs%n",
                    audioDuration,
                    sessionDuration
            );
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.printf("%nWebSocket disconnected: Status=%d, Msg=%s%n", code, reason);
            stopRequested.set(true);
            shutdownLatch.countDown();
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("\nWebSocket Error: " + ex.getMessage());
            stopRequested.set(true);
            shutdownLatch.countDown();
        }
    }
}

package com.dstteam.zhuoctopus.airvirtuoso.ncp;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * CLOVA Speech TTS Service for Naver Cloud Platform
 * Provides high-quality text-to-speech with Korean and English support
 */
public class ClovaSpeechService {
    private static final String TAG = "ClovaSpeechService";
    
    // API Configuration - Replace with your actual credentials
    // Get these from: https://www.ncloud.com/ > AI·NAVER API > Application
    private static final String CLIENT_ID = "YOUR_CLIENT_ID";
    private static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET";
    private static final String API_URL = "https://naveropenapi.apigw.ntruss.com/voice/v1/tts";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private MediaPlayer currentMediaPlayer;
    
    // Available speakers
    public enum Speaker {
        MIJIN("mijin", "Korean Female"),
        JINHO("jinho", "Korean Male"),
        CLARA("clara", "English Female"),
        MATT("matt", "English Male");
        
        private final String code;
        private final String description;
        
        Speaker(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public interface TtsCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public ClovaSpeechService(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Synthesize and play text using CLOVA Speech TTS
     * 
     * @param text Text to synthesize
     * @param speaker Speaker voice to use
     * @param speed Speech speed (-5 to 5, default 0)
     * @param callback Callback for success/error
     */
    public void speak(String text, Speaker speaker, int speed, TtsCallback callback) {
        if (CLIENT_ID.equals("YOUR_CLIENT_ID") || CLIENT_SECRET.equals("YOUR_CLIENT_SECRET")) {
            Log.w(TAG, "CLOVA Speech API credentials not configured. Please set CLIENT_ID and CLIENT_SECRET.");
            if (callback != null) {
                callback.onError("API credentials not configured");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Stop any currently playing audio
                stop();
                
                // Encode text
                String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
                
                // Build request parameters
                String params = String.format("speaker=%s&speed=%d&text=%s", 
                    speaker.getCode(), speed, encodedText);
                
                // Create request
                RequestBody requestBody = RequestBody.create(
                    params, 
                    MediaType.parse("application/x-www-form-urlencoded")
                );
                
                Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("X-NCP-APIGW-API-KEY-ID", CLIENT_ID)
                    .addHeader("X-NCP-APIGW-API-KEY", CLIENT_SECRET)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .post(requestBody)
                    .build();
                
                // Execute request
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "CLOVA Speech API request failed", e);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError(e.getMessage()));
                        }
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            // Save audio to temporary file
                            File tempFile = new File(context.getCacheDir(), "clova_tts_" + System.currentTimeMillis() + ".mp3");
                            try (InputStream inputStream = response.body().byteStream();
                                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                                
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }
                            
                            // Play audio on main thread
                            mainHandler.post(() -> {
                                try {
                                    currentMediaPlayer = new MediaPlayer();
                                    currentMediaPlayer.setDataSource(tempFile.getAbsolutePath());
                                    currentMediaPlayer.prepare();
                                    currentMediaPlayer.setOnCompletionListener(mp -> {
                                        mp.release();
                                        tempFile.delete(); // Clean up
                                        if (callback != null) {
                                            callback.onSuccess();
                                        }
                                    });
                                    currentMediaPlayer.start();
                                } catch (IOException e) {
                                    Log.e(TAG, "Failed to play TTS audio", e);
                                    tempFile.delete();
                                    if (callback != null) {
                                        callback.onError(e.getMessage());
                                    }
                                }
                            });
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                            Log.e(TAG, "CLOVA Speech API error: " + response.code() + " - " + errorBody);
                            if (callback != null) {
                                mainHandler.post(() -> callback.onError("API error: " + response.code()));
                            }
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error synthesizing speech", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }
    
    /**
     * Convenience method with default settings (Korean female voice, normal speed)
     */
    public void speak(String text, TtsCallback callback) {
        speak(text, Speaker.MIJIN, 0, callback);
    }
    
    /**
     * Stop currently playing audio
     */
    public void stop() {
        if (currentMediaPlayer != null) {
            try {
                if (currentMediaPlayer.isPlaying()) {
                    currentMediaPlayer.stop();
                }
                currentMediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
            currentMediaPlayer = null;
        }
    }
    
    /**
     * Release resources
     */
    public void release() {
        stop();
        executorService.shutdown();
    }
}

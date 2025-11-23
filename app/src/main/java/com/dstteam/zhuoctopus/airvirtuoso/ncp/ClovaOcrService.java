package com.dstteam.zhuoctopus.airvirtuoso.ncp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
 * CLOVA OCR Service for Naver Cloud Platform
 * Extracts text and musical notation from sheet music images
 */
public class ClovaOcrService {
    private static final String TAG = "ClovaOcrService";
    
    // API Configuration - Replace with your actual credentials
    // Get these from: https://www.ncloud.com/ > AI Services > CLOVA OCR > Domain > API Gateway Integration
    private static final String CLIENT_ID = "YOUR_CLIENT_ID"; // Access Key ID from My Page > Security > Authentication Key
    private static final String CLIENT_SECRET = "ZEhSWHFGbUNtRGJPSmhNbm5TYWd6YXh5T3huZG5oZXU="; // Your Secret Key
    private static final String API_URL = "YOUR_INVOKE_URL"; // From API Gateway Integration > Invoke URL
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Gson gson;
    
    /**
     * OCR Result containing extracted text and bounding boxes
     */
    public static class OcrResult {
        public final String text;
        public final List<TextBlock> textBlocks;
        
        public OcrResult(String text, List<TextBlock> textBlocks) {
            this.text = text;
            this.textBlocks = textBlocks;
        }
        
        public static class TextBlock {
            public final String text;
            public final BoundingBox boundingBox;
            
            public TextBlock(String text, BoundingBox boundingBox) {
                this.text = text;
                this.boundingBox = boundingBox;
            }
        }
        
        public static class BoundingBox {
            public final int x, y, width, height;
            
            public BoundingBox(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }
    }
    
    public interface OcrCallback {
        void onSuccess(OcrResult result);
        void onError(String error);
    }
    
    public ClovaOcrService(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
    }
    
    /**
     * Perform OCR on a bitmap image
     * 
     * @param bitmap Image to process
     * @param callback Callback for result
     */
    public void recognizeImage(Bitmap bitmap, OcrCallback callback) {
        if (CLIENT_ID.equals("YOUR_CLIENT_ID") || CLIENT_SECRET.equals("YOUR_CLIENT_SECRET") || 
            API_URL.equals("YOUR_INVOKE_URL")) {
            Log.w(TAG, "CLOVA OCR API credentials not configured. Please set CLIENT_ID, CLIENT_SECRET, and API_URL.");
            if (callback != null) {
                callback.onError("API credentials not configured");
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Convert bitmap to base64
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                byte[] imageBytes = outputStream.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                
                // Create JSON request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("version", "V2");
                requestBody.addProperty("requestId", "string");
                requestBody.addProperty("timestamp", System.currentTimeMillis());
                requestBody.addProperty("images", base64Image);
                
                // Create request
                RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
                );
                
                Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("X-OCR-SECRET", CLIENT_SECRET)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
                
                // Execute request
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "CLOVA OCR API request failed", e);
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseBody = response.body().string();
                            try {
                                OcrResult result = parseOcrResponse(responseBody);
                                if (callback != null) {
                                    callback.onSuccess(result);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse OCR response", e);
                                if (callback != null) {
                                    callback.onError("Failed to parse response: " + e.getMessage());
                                }
                            }
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                            Log.e(TAG, "CLOVA OCR API error: " + response.code() + " - " + errorBody);
                            if (callback != null) {
                                callback.onError("API error: " + response.code());
                            }
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error performing OCR", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Parse CLOVA OCR API response
     */
    private OcrResult parseOcrResponse(String jsonResponse) {
        JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonArray images = root.getAsJsonArray("images");
        
        if (images == null || images.size() == 0) {
            return new OcrResult("", new ArrayList<>());
        }
        
        JsonObject imageData = images.get(0).getAsJsonObject();
        JsonArray fields = imageData.getAsJsonArray("fields");
        
        List<OcrResult.TextBlock> textBlocks = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                JsonObject field = fields.get(i).getAsJsonObject();
                String text = field.has("inferText") ? field.get("inferText").getAsString() : "";
                fullText.append(text).append(" ");
                
                // Parse bounding box if available
                OcrResult.BoundingBox boundingBox = null;
                if (field.has("boundingPoly")) {
                    JsonObject boundingPoly = field.getAsJsonObject("boundingPoly");
                    if (boundingPoly.has("vertices")) {
                        JsonArray vertices = boundingPoly.getAsJsonArray("vertices");
                        if (vertices.size() >= 2) {
                            int x = vertices.get(0).getAsJsonObject().get("x").getAsInt();
                            int y = vertices.get(0).getAsJsonObject().get("y").getAsInt();
                            int width = vertices.get(2).getAsJsonObject().get("x").getAsInt() - x;
                            int height = vertices.get(2).getAsJsonObject().get("y").getAsInt() - y;
                            boundingBox = new OcrResult.BoundingBox(x, y, width, height);
                        }
                    }
                }
                
                textBlocks.add(new OcrResult.TextBlock(text, boundingBox));
            }
        }
        
        return new OcrResult(fullText.toString().trim(), textBlocks);
    }
    
    /**
     * Release resources
     */
    public void release() {
        executorService.shutdown();
    }
}

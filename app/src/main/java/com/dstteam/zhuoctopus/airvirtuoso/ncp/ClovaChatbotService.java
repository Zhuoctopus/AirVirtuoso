package com.dstteam.zhuoctopus.airvirtuoso.ncp;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Service for interacting with CLOVA Chatbot API
 * Focused on piano knowledge and music education
 */
public class ClovaChatbotService {
    private static final String TAG = "ClovaChatbotService";
    
    // API Configuration
    // Domain: AirVirtuoso (ID: 18460)
    private static final String DOMAIN_ID = "18460";
    private static final String DOMAIN_CODE = "AirVirtuoso";
    private static final String SECRET_KEY = "QlhyVFF4RmlrRHRoWEtCc0V2YkNlWGl3ZUZnbUNvVmo="; // Secret Key from API Gateway Integration
    private static final String INVOKE_URL = "https://clovachatbot.ncloud.com/api/chatbot/messenger/v1/18460/2251dfef430e8ea9a49692e57a8a82e6a52a4f2c4cf804cc971367e6977245ae"; // Invoke URL from API Gateway Integration
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    private final Gson gson;
    private String sessionId;
    
    /**
     * Chatbot response containing message and metadata
     */
    public static class ChatResponse {
        public final String message;
        public final String sessionId;
        public final boolean success;
        public final String error;
        
        public ChatResponse(String message, String sessionId, boolean success, String error) {
            this.message = message;
            this.sessionId = sessionId;
            this.success = success;
            this.error = error;
        }
    }
    
    public interface ChatCallback {
        void onSuccess(ChatResponse response);
        void onError(String error);
    }
    
    public ClovaChatbotService(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executorService = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
        this.sessionId = UUID.randomUUID().toString(); // Generate unique session ID
    }
    
    /**
     * Send a message to the chatbot
     * 
     * @param userMessage The user's message
     * @param callback Callback for handling response
     */
    public void sendMessage(String userMessage, ChatCallback callback) {
        // API credentials are configured
        
        executorService.execute(() -> {
            try {
                // Validate message is not empty
                if (userMessage == null || userMessage.trim().isEmpty()) {
                    callback.onError("Message cannot be empty");
                    return;
                }
                
                String trimmedMessage = userMessage.trim();
                Log.d(TAG, "Sending message: " + trimmedMessage);
                
                // API Gateway expects a simple format with top-level "text" field
                // Format: {"version":"v2","userId":"...","timestamp":<number>,"text":"message"}
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("version", "v2");
                requestBody.addProperty("userId", "AirVirtuosoUser");
                
                // Timestamp should be a number (milliseconds), not ISO 8601 string
                long timestamp = System.currentTimeMillis();
                requestBody.addProperty("timestamp", timestamp);
                
                // The API Gateway expects "text" field at top level
                requestBody.addProperty("text", trimmedMessage);
                
                Log.d(TAG, "Using API Gateway format with top-level text field");
                Log.d(TAG, "Text value: " + trimmedMessage);
                Log.d(TAG, "Timestamp (milliseconds): " + timestamp);
                
                // Convert to JSON string - ensure consistent formatting
                String requestBodyJson = gson.toJson(requestBody);
                Log.d(TAG, "Request body JSON: " + requestBodyJson);
                
                // Verify the message is in the JSON
                if (!requestBodyJson.contains(trimmedMessage)) {
                    Log.e(TAG, "WARNING: Message not found in request body JSON!");
                    Log.e(TAG, "Message: " + trimmedMessage);
                    Log.e(TAG, "JSON: " + requestBodyJson);
                }
                
                // Verify the structure has required fields
                if (!requestBodyJson.contains("\"text\"")) {
                    Log.e(TAG, "WARNING: 'text' field not found in request body!");
                }
                if (!requestBodyJson.contains("\"timestamp\"")) {
                    Log.e(TAG, "WARNING: 'timestamp' field not found in request body!");
                }
                if (!requestBodyJson.contains("\"userId\"")) {
                    Log.e(TAG, "WARNING: 'userId' field not found in request body!");
                }
                
                // Generate signature (must be generated from the exact JSON string)
                String signature = generateSignature(requestBodyJson);
                Log.d(TAG, "Generated signature: " + signature);
                
                // Log the full request structure for debugging
                Log.d(TAG, "Full request structure:");
                Log.d(TAG, "  version: v2");
                Log.d(TAG, "  userId: AirVirtuosoUser");
                Log.d(TAG, "  timestamp: " + requestBody.get("timestamp").getAsLong());
                Log.d(TAG, "  text: " + trimmedMessage);
                
                // Create HTTP request
                RequestBody body = RequestBody.create(
                    requestBodyJson,
                    MediaType.parse("application/json; charset=UTF-8")
                );
                
                // Ensure URL is trimmed (no trailing spaces)
                String trimmedUrl = INVOKE_URL.trim();
                // Remove trailing slash if present (some APIs are sensitive to this)
                if (trimmedUrl.endsWith("/")) {
                    trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
                }
                
                // According to Swagger, the endpoint is /message
                // The API Gateway requires /message to be appended to the Invoke URL
                String finalUrl = trimmedUrl;
                if (!trimmedUrl.endsWith("/message")) {
                    finalUrl = trimmedUrl + "/message";
                    Log.d(TAG, "Appending /message to URL: " + finalUrl);
                } else {
                    Log.d(TAG, "URL already ends with /message: " + finalUrl);
                }
                
                Log.d(TAG, "Request URL: " + finalUrl);
                Log.d(TAG, "Request method: POST");
                
                // Build request with required headers
                // According to Swagger spec, API Gateway expects x-ncp-apigw-api-key header
                Request.Builder requestBuilder = new Request.Builder()
                        .url(finalUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json; charset=UTF-8");
                
                // Add API Gateway API key header (from Swagger spec)
                // The Secret Key might be Base64-encoded, try both formats
                // First try: use Secret Key as-is
                requestBuilder.addHeader("x-ncp-apigw-api-key", SECRET_KEY);
                Log.d(TAG, "Added x-ncp-apigw-api-key header");
                
                // Add CLOVA Chatbot signature header
                requestBuilder.addHeader("X-NCP-CHATBOT_SIGNATURE", signature);
                Log.d(TAG, "Added X-NCP-CHATBOT_SIGNATURE header");
                
                Request request = requestBuilder.build();
                
                // Execute request
                Response response = httpClient.newCall(request).execute();
                
                // Log response headers for debugging
                Log.d(TAG, "Response code: " + response.code());
                Log.d(TAG, "Response headers: " + response.headers().toString());
                
                // Note: We now always append /message to the URL, so this retry logic shouldn't be needed
                // But keeping it as a safety fallback
                if (response.code() == 405 && !finalUrl.endsWith("/message")) {
                    Log.w(TAG, "Got 405, trying with /message appended to URL");
                    String errorBody = response.body() != null ? response.body().string() : "";
                    response.close(); // Close the first response
                    
                    String urlWithMessage = finalUrl + "/message";
                    Log.d(TAG, "Retrying with URL: " + urlWithMessage);
                    
                    // Rebuild request with new URL
                    Request retryRequest = new Request.Builder()
                            .url(urlWithMessage)
                            .post(body)
                            .addHeader("Content-Type", "application/json; charset=UTF-8")
                            .addHeader("x-ncp-apigw-api-key", SECRET_KEY)
                            .addHeader("X-NCP-CHATBOT_SIGNATURE", signature)
                            .build();
                    
                    response = httpClient.newCall(retryRequest).execute();
                    Log.d(TAG, "Retry response code: " + response.code());
                    Log.d(TAG, "Retry response headers: " + response.headers().toString());
                }
                
                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API error: " + response.code() + " - " + responseBody);
                        Log.e(TAG, "Request URL: " + finalUrl);
                        Log.e(TAG, "Request body: " + requestBodyJson);
                        Log.e(TAG, "Request headers: " + request.headers().toString());
                        
                        // Try to parse error response for more details
                        try {
                            JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                            if (errorJson.has("resultCode")) {
                                int resultCode = errorJson.get("resultCode").getAsInt();
                                String resultMessage = errorJson.has("resultMessage") 
                                    ? errorJson.get("resultMessage").getAsString() : "Unknown error";
                                Log.e(TAG, "Error resultCode: " + resultCode + ", resultMessage: " + resultMessage);
                                
                                // If it's "Message contents is empty", the structure might be wrong
                                if (resultCode == 99 && resultMessage.contains("empty")) {
                                    Log.e(TAG, "Message content issue detected. Checking request structure...");
                                    Log.e(TAG, "Message sent: " + trimmedMessage);
                                    Log.e(TAG, "Full request JSON: " + requestBodyJson);
                                    
                                    // Try to verify the JSON structure
                                    try {
                                        JsonObject parsedRequest = gson.fromJson(requestBodyJson, JsonObject.class);
                                        Log.e(TAG, "Parsed request structure: " + parsedRequest.toString());
                                        
                                        // Check for v2 format (inputs)
                                        if (parsedRequest.has("inputs")) {
                                            com.google.gson.JsonArray inputs = parsedRequest.getAsJsonArray("inputs");
                                            Log.e(TAG, "Found inputs array with " + inputs.size() + " items");
                                            if (inputs.size() > 0) {
                                                JsonObject firstInput = inputs.get(0).getAsJsonObject();
                                                Log.e(TAG, "First input: " + firstInput.toString());
                                                if (firstInput.has("text")) {
                                                    String text = firstInput.get("text").getAsString();
                                                    Log.e(TAG, "Text value: '" + text + "' (length: " + text.length() + ")");
                                                } else {
                                                    Log.e(TAG, "ERROR: 'text' field missing from input!");
                                                }
                                            }
                                        } else {
                                            Log.e(TAG, "ERROR: 'inputs' field missing from request!");
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing request JSON for debugging", e);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Could not parse error response as JSON", e);
                        }
                        
                        // Check for Allow header (shows allowed methods)
                        String allowHeader = response.header("Allow");
                        if (allowHeader != null) {
                            Log.e(TAG, "Allowed methods: " + allowHeader);
                        }
                        
                        response.close();
                        callback.onError("API error " + response.code() + ": " + responseBody);
                        return;
                    }
                    
                    // Parse response
                    Log.d(TAG, "Parsing response body: " + responseBody);
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    
                    // Log all top-level keys to understand response structure
                    Log.d(TAG, "Response keys: " + jsonResponse.keySet().toString());
                    
                    // Check for success flag first
                    if (jsonResponse.has("success")) {
                        boolean success = jsonResponse.get("success").getAsBoolean();
                        if (!success) {
                            Log.e(TAG, "API returned success: false");
                            // Will check resultCode below
                        }
                    }
                    
                    // Check for error codes in response (even if HTTP status is 200)
                    // API Gateway may return resultCode in the response body (as string or number)
                    if (jsonResponse.has("resultCode")) {
                        int resultCode = -1;
                        try {
                            // Try as number first
                            resultCode = jsonResponse.get("resultCode").getAsInt();
                        } catch (Exception e) {
                            // If that fails, try as string
                            try {
                                resultCode = Integer.parseInt(jsonResponse.get("resultCode").getAsString());
                            } catch (Exception e2) {
                                Log.e(TAG, "Could not parse resultCode", e2);
                            }
                        }
                        
                        Log.d(TAG, "Found resultCode: " + resultCode);
                        
                        if (resultCode != 0 && resultCode != 200) { // 0 or 200 typically means success
                            String resultMessage = "Unknown error";
                            
                            // Try different possible message fields
                            if (jsonResponse.has("resultMessage")) {
                                resultMessage = jsonResponse.get("resultMessage").getAsString();
                            } else if (jsonResponse.has("message")) {
                                resultMessage = jsonResponse.get("message").getAsString();
                            } else if (jsonResponse.has("error")) {
                                JsonObject error = jsonResponse.getAsJsonObject("error");
                                if (error.has("message")) {
                                    resultMessage = error.get("message").getAsString();
                                } else {
                                    resultMessage = error.toString();
                                }
                            }
                            
                            Log.e(TAG, "API returned error resultCode: " + resultCode + ", message: " + resultMessage);
                            Log.e(TAG, "Full response: " + responseBody);
                            
                            // Common error codes and their meanings
                            String errorDescription = "";
                            switch (resultCode) {
                                case 99:
                                    errorDescription = " (Request validation failed - check request format)";
                                    break;
                                case 400:
                                    errorDescription = " (Bad Request)";
                                    break;
                                case 401:
                                    errorDescription = " (Unauthorized - check API credentials)";
                                    break;
                                case 403:
                                    errorDescription = " (Forbidden)";
                                    break;
                                case 500:
                                    errorDescription = " (Internal Server Error)";
                                    break;
                            }
                            
                            callback.onError("API error (resultCode " + resultCode + errorDescription + "): " + resultMessage);
                            return;
                        }
                    }
                    
                    // Check for status object format (some CLOVA APIs use this)
                    if (jsonResponse.has("status")) {
                        JsonObject status = jsonResponse.getAsJsonObject("status");
                        String statusCode = status.has("code") ? status.get("code").getAsString() : "";
                        String statusMessage = status.has("message") ? status.get("message").getAsString() : "";
                        
                        // Check if status indicates error (not "20000" or "OK")
                        if (!statusCode.equals("20000") && !statusMessage.equals("OK")) {
                            Log.e(TAG, "API returned error status: " + statusCode + ", message: " + statusMessage);
                            Log.e(TAG, "Full response: " + responseBody);
                            callback.onError("API error (status " + statusCode + "): " + statusMessage);
                            return;
                        }
                        
                        // If status is OK, try to extract message from result
                        if (jsonResponse.has("result")) {
                            JsonObject result = jsonResponse.getAsJsonObject("result");
                            if (result.has("message")) {
                                JsonObject messageObj = result.getAsJsonObject("message");
                                if (messageObj.has("content")) {
                                    String message = messageObj.get("content").getAsString();
                                    if (message != null && !message.isEmpty()) {
                                        callback.onSuccess(new ChatResponse(message, sessionId, true, null));
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Try CLOVA Chatbot API v2 response format: outputs array
                    if (jsonResponse.has("outputs")) {
                        com.google.gson.JsonArray outputs = jsonResponse.getAsJsonArray("outputs");
                        if (outputs.size() > 0) {
                            JsonObject firstOutput = outputs.get(0).getAsJsonObject();
                            if (firstOutput.has("text")) {
                                String message = firstOutput.get("text").getAsString();
                                if (message != null && !message.isEmpty()) {
                                    callback.onSuccess(new ChatResponse(message, sessionId, true, null));
                                    return;
                                }
                            }
                        }
                    }
                    
                    // CLOVA Chatbot API v1 response format: bubbles array (fallback)
                    if (jsonResponse.has("bubbles")) {
                        com.google.gson.JsonArray bubbles = jsonResponse.getAsJsonArray("bubbles");
                        if (bubbles.size() > 0) {
                            JsonObject firstBubble = bubbles.get(0).getAsJsonObject();
                            if (firstBubble.has("data")) {
                                JsonObject data = firstBubble.getAsJsonObject("data");
                                String message = null;
                                
                                // Try to get description (text message)
                                if (data.has("description")) {
                                    message = data.get("description").getAsString();
                                } else if (data.has("text")) {
                                    message = data.get("text").getAsString();
                                } else if (data.has("data")) {
                                    // Sometimes data is nested
                                    message = data.get("data").getAsString();
                                }
                                
                                if (message != null && !message.isEmpty()) {
                                    // Update session ID if provided
                                    if (jsonResponse.has("sessionId")) {
                                        sessionId = jsonResponse.get("sessionId").getAsString();
                                    }
                                    
                                    callback.onSuccess(new ChatResponse(message, sessionId, true, null));
                                    return;
                                }
                            }
                        }
                    }
                    
                    // Fallback: try to extract message from different response formats
                    String message = extractMessageFromResponse(jsonResponse);
                    if (message != null && !message.isEmpty()) {
                        callback.onSuccess(new ChatResponse(message, sessionId, true, null));
                    } else {
                        // Log the full response structure for debugging
                        Log.e(TAG, "Could not parse response. Full response structure:");
                        Log.e(TAG, "  Response keys: " + jsonResponse.keySet().toString());
                        Log.e(TAG, "  Full JSON: " + jsonResponse.toString());
                        Log.e(TAG, "  Raw response: " + responseBody);
                        
                        // Check if there's a resultCode we missed
                        if (jsonResponse.has("resultCode")) {
                            int resultCode = jsonResponse.get("resultCode").getAsInt();
                            String resultMessage = jsonResponse.has("resultMessage") 
                                ? jsonResponse.get("resultMessage").getAsString() 
                                : "No message provided";
                            callback.onError("API error (resultCode " + resultCode + "): " + resultMessage);
                        } else {
                            callback.onError("Unexpected response format. Response keys: " + jsonResponse.keySet().toString() + ". Full response: " + responseBody);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message to chatbot", e);
                    callback.onError("Error: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message to chatbot", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate HMAC-SHA256 signature for CLOVA Chatbot API
     * Try both Base64-decoded and raw secret key formats
     */
    private String generateSignature(String requestBody) {
        // First try: Use secret key as-is (raw bytes)
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hash);
            Log.d(TAG, "Signature (raw key): " + signature);
            return signature;
        } catch (Exception e) {
            Log.w(TAG, "Failed with raw key, trying Base64 decoded", e);
        }
        
        // Second try: Decode Base64 secret key first
        try {
            byte[] secretKeyBytes = Base64.getDecoder().decode(SECRET_KEY);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKeyBytes,
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hash);
            Log.d(TAG, "Signature (Base64 decoded key): " + signature);
            return signature;
        } catch (Exception e) {
            Log.e(TAG, "Both signature methods failed", e);
            return "";
        }
    }
    
    /**
     * Extract message from various response formats
     */
    private String extractMessageFromResponse(JsonObject jsonResponse) {
        // Try bubbles array format (CLOVA Chatbot API v1)
        if (jsonResponse.has("bubbles")) {
            com.google.gson.JsonArray bubbles = jsonResponse.getAsJsonArray("bubbles");
            if (bubbles.size() > 0) {
                JsonObject bubble = bubbles.get(0).getAsJsonObject();
                if (bubble.has("data")) {
                    JsonObject data = bubble.getAsJsonObject("data");
                    if (data.has("description")) {
                        return data.get("description").getAsString();
                    }
                    if (data.has("text")) {
                        return data.get("text").getAsString();
                    }
                }
            }
        }
        
        // Try event.bubble format (alternative format)
        if (jsonResponse.has("event")) {
            JsonObject event = jsonResponse.getAsJsonObject("event");
            if (event.has("bubble")) {
                JsonObject bubble = event.getAsJsonObject("bubble");
                if (bubble.has("data")) {
                    JsonObject data = bubble.getAsJsonObject("data");
                    if (data.has("description")) {
                        return data.get("description").getAsString();
                    }
                    return data.toString(); // Fallback
                }
            }
        }
        
        // Try direct message fields
        if (jsonResponse.has("message")) {
            return jsonResponse.get("message").getAsString();
        }
        
        if (jsonResponse.has("text")) {
            return jsonResponse.get("text").getAsString();
        }
        
        return null;
    }
    
    /**
     * Get current session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Reset session (start new conversation)
     */
    public void resetSession() {
        this.sessionId = UUID.randomUUID().toString();
    }
    
    /**
     * Release resources
     */
    public void release() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}

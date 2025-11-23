package com.dstteam.zhuoctopus.airvirtuoso.ncp;

/**
 * Configuration class for Naver Cloud Platform API credentials
 * 
 * SETUP INSTRUCTIONS:
 * 
 * 1. CLOVA Speech (TTS):
 *    - Go to https://www.ncloud.com/
 *    - Navigate to: AI·NAVER API > Application
 *    - Register your application
 *    - Copy Client ID and Client Secret
 *    - Update ClovaSpeechService.java with your credentials
 * 
 * 2. CLOVA OCR:
 *    - Go to https://www.ncloud.com/
 *    - Navigate to: AI Services > CLOVA OCR
 *    - Create a Domain (General type recommended)
 *    - Go to API Gateway Integration tab
 *    - Click "Integrate" to get Invoke URL
 *    - Get Access Key ID and Secret Key from: My Page > Security > Authentication Key
 *    - Update ClovaOcrService.java with your credentials
 * 
 * Note: For production, consider storing credentials securely (e.g., BuildConfig, encrypted storage)
 */
public class NcpConfig {
    // This class serves as documentation for API setup
    // Actual credentials are stored in respective service classes
    // to keep them close to where they're used
}

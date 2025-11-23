package com.dstteam.zhuoctopus.airvirtuoso.ncp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.dstteam.zhuoctopus.airvirtuoso.model.Song;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class to import sheet music from images using CLOVA OCR
 */
public class SheetMusicImporter {
    private static final String TAG = "SheetMusicImporter";
    
    private final ClovaOcrService ocrService;
    private final Context context;
    
    public interface ImportCallback {
        void onSuccess(Song song);
        void onError(String error);
    }
    
    public SheetMusicImporter(Context context) {
        this.context = context;
        this.ocrService = new ClovaOcrService(context);
    }
    
    /**
     * Import sheet music from a bitmap image
     * 
     * @param bitmap Image containing sheet music
     * @param songTitle Title for the song
     * @param callback Callback for result
     */
    public void importFromBitmap(Bitmap bitmap, String songTitle, ImportCallback callback) {
        if (bitmap == null) {
            if (callback != null) {
                callback.onError("Bitmap is null");
            }
            return;
        }
        
        ocrService.recognizeImage(bitmap, new ClovaOcrService.OcrCallback() {
            @Override
            public void onSuccess(ClovaOcrService.OcrResult result) {
                Log.d(TAG, "OCR Result: " + result.text);
                
                // Parse OCR text into notes
                Song song = SheetMusicParser.parseOcrText(result.text, songTitle);
                
                if (song != null) {
                    if (callback != null) {
                        callback.onSuccess(song);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("Failed to parse musical notation from OCR text: " + result.text);
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "OCR failed: " + error);
                if (callback != null) {
                    callback.onError("OCR failed: " + error);
                }
            }
        });
    }
    
    /**
     * Import sheet music from a URI (e.g., from gallery)
     * 
     * @param uri URI of the image
     * @param songTitle Title for the song
     * @param callback Callback for result
     */
    public void importFromUri(Uri uri, String songTitle, ImportCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                if (callback != null) {
                    callback.onError("Failed to open image URI");
                }
                return;
            }
            
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (bitmap == null) {
                if (callback != null) {
                    callback.onError("Failed to decode image");
                }
                return;
            }
            
            importFromBitmap(bitmap, songTitle, callback);
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading image from URI", e);
            if (callback != null) {
                callback.onError("Error reading image: " + e.getMessage());
            }
        }
    }
    
    /**
     * Release resources
     */
    public void release() {
        ocrService.release();
    }
}

package com.dstteam.zhuoctopus.airvirtuoso;

import android.Manifest;
import android.animation.LayoutTransition;
import android.content.pm.PackageManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.snackbar.Snackbar;
import com.dstteam.zhuoctopus.airvirtuoso.databinding.ActivityMainBinding;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dstteam.zhuoctopus.airvirtuoso.databinding.ActivityMainBinding;
import com.dstteam.zhuoctopus.airvirtuoso.logic.SheetMusicEngine;
import com.dstteam.zhuoctopus.airvirtuoso.ml.HandLandmarkerHelper;
import com.dstteam.zhuoctopus.airvirtuoso.model.Note;
import com.dstteam.zhuoctopus.airvirtuoso.model.Song;
import com.dstteam.zhuoctopus.airvirtuoso.ui.PianoOverlayView;
import com.dstteam.zhuoctopus.airvirtuoso.ui.SheetPreviewAdapter;
import com.dstteam.zhuoctopus.airvirtuoso.ui.SongAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.SnapHelper;
import androidx.recyclerview.widget.LinearSmoothScroller;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.dstteam.zhuoctopus.airvirtuoso.analysis.PostureAnalyzer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.sidesheet.SideSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.dstteam.zhuoctopus.airvirtuoso.ui.SongCardAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "AirVirtuoso";
    private ActivityMainBinding binding;
    private ExecutorService cameraExecutor;
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private Sensor linearAccelerationSensor;
    private AudioManager audioManager;

    private boolean isDeviceMoving = false;
    private boolean isDeviceAngleIncorrect = false;
    private long lastMovementTime = 0;

    private static final int ACCEL_BUFFER_SIZE = 50;
    private final float[] accelBuffer = new float[ACCEL_BUFFER_SIZE];
    private int accelBufferIndex = 0;
    private boolean isBufferFull = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
    
    private final ActivityResultLauncher<String> requestAudioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                Log.d(TAG, "Audio permission result: " + isGranted);
                if (isGranted) {
                    showSnackbar("Microphone permission granted! Starting voice recognition...");
                    startVoiceRecognition();
                } else {
                    showSnackbar("Microphone permission denied. Voice commands won't work.");
                    // Optionally show dialog explaining why permission is needed
                    if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Microphone Permission Needed")
                            .setMessage("Voice commands require microphone access. Please grant permission in app settings.")
                            .setPositiveButton("OK", null)
                            .show();
                    }
                }
            });

    private HandLandmarkerHelper handLandmarkerHelper;
    private PianoOverlayView pianoOverlay;
    private com.dstteam.zhuoctopus.airvirtuoso.audio.AudioEngine audioEngine;
    private Vibrator vibrator;
    private SheetMusicEngine sheetMusicEngine;
    private boolean isPlayBySheetMode = false;
    private long lastHandDetectionTime = 0;
    private long lastTwoHandsTime = 0;
    private long lastHiFiveGestureTime = 0; // Cooldown for hi-five gesture detection
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private long lastTryAgainSpeakTime = 0;
    private static final long TRY_AGAIN_COOLDOWN_MS = 3000;
    private static final float TRY_AGAIN_VOLUME = 0.5f;
    private com.dstteam.zhuoctopus.airvirtuoso.analysis.TimingAnalyzer timingAnalyzer;
    
    private android.speech.SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private boolean hasIncorrectKeyPress = false;
    private long lastKeyPressTime = 0;
    private static final long KEY_PRESS_GRACE_PERIOD_MS = 300; // 300ms window

    private boolean isPostCompletionCooldown = false;
    private static final long POST_COMPLETION_COOLDOWN_MS = 2000; // 2 seconds
    private boolean isDemoMode = false; // Flag to skip validation during demo

    private List<View> pianoKeyViews = new ArrayList<>();
    private SheetPreviewAdapter sheetPreviewAdapter;
    private ProgressBar progressBar;
    private String lastSongTitle = ""; // Track song changes
    private TextView previewSongTitle;
    private RecyclerView noteStreamRecyclerView;
    private View sheetPreviewView;
    private ChipGroup warningChipGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        warningChipGroup = binding.warningChipGroup;
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(150);
        warningChipGroup.setLayoutTransition(layoutTransition);

        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pianoOverlay = binding.pianoOverlay;
        audioEngine = new com.dstteam.zhuoctopus.airvirtuoso.audio.AudioEngine(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(java.util.Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                }
            }
        });
        
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new VoiceCommandListener());
            Log.d(TAG, "Speech recognition initialized successfully");
        } else {
            Log.w(TAG, "Speech recognition not available on this device");
            showSnackbar("Voice commands not available on this device");
        }

        sheetMusicEngine = new SheetMusicEngine();
        timingAnalyzer = new com.dstteam.zhuoctopus.airvirtuoso.analysis.TimingAnalyzer();

        setupToolbar();
        setupPianoKeys();
        setupSheetPreview();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        cameraExecutor = Executors.newSingleThreadExecutor();

        cameraExecutor.execute(() -> {
            handLandmarkerHelper = new HandLandmarkerHelper(this, new HandLandmarkerHelper.LandmarkerListener() {
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> showSnackbar(error));
                }

                @Override
                public void onResults(com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult result,
                        int inputImageWidth, int inputImageHeight) {
                    runOnUiThread(() -> {
                        pianoOverlay.setHandLandmarkerResult(result, inputImageWidth, inputImageHeight);
                        // Update posture analysis
                        pianoOverlay.updatePosture();

                        // Clear old warnings
                        clearWarningChips();

                        // Check for warnings
                        if (result.landmarks().isEmpty()) {
                            showWarningChip("No hands detected", android.R.drawable.ic_dialog_alert, R.color.info_blue);
                        } else {
                            // Analyze posture for each hand
                            for (List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks : result
                                    .landmarks()) {
                                PostureAnalyzer.PostureResult postureResult = PostureAnalyzer.analyzePosture(landmarks);

                                if (postureResult.status == PostureAnalyzer.PostureStatus.DANGER) {
                                    String msg = postureResult.message.replaceAll("[⚠️✓⚡]", "").trim();
                                    showWarningChip(msg, android.R.drawable.ic_dialog_alert, R.color.danger_red);
                                } else if (postureResult.status == PostureAnalyzer.PostureStatus.WARNING) {
                                    String msg = postureResult.message.replaceAll("[⚠️✓⚡]", "").trim();
                                    showWarningChip(msg, android.R.drawable.ic_dialog_info, R.color.warning_yellow);
                                }
                            }

                            // Check if two hands detected (good!)
                            if (result.landmarks().size() >= 2) {
                                showWarningChip("Two hands detected - great!", android.R.drawable.ic_dialog_info,
                                        R.color.info_blue);
                            }
                        }
                    });

                    if (!result.landmarks().isEmpty()) {
                        lastHandDetectionTime = System.currentTimeMillis();
                        if (result.landmarks().size() >= 2) {
                            lastTwoHandsTime = System.currentTimeMillis();
                        }
                        
                        // Detect hi-five gesture for voice command activation
                        for (List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks : result.landmarks()) {
                            if (com.dstteam.zhuoctopus.airvirtuoso.analysis.GestureDetector.isHiFiveGesture(landmarks)) {
                                long currentTime = System.currentTimeMillis();
                                // Cooldown: only trigger once every 3 seconds
                                if (currentTime - lastHiFiveGestureTime > 3000) {
                                    lastHiFiveGestureTime = currentTime;
                                    runOnUiThread(() -> {
                                        showSnackbar("👋 Hi-five detected! Activating voice commands...");
                                        startVoiceRecognition();
                                    });
                                }
                                break; // Only trigger once per frame
                            }
                        }
                    }
                    runOnUiThread(
                            () -> pianoOverlay.setHandLandmarkerResult(result, inputImageWidth, inputImageHeight));
                }
            });
        });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupPianoKeys() {
        binding.pianoKeysContainer.removeAllViews();
        pianoKeyViews.clear();

        int[] semitonesFromA3 = { 0, 2, 3, 5, 7, 8, 10, 12, 14, 15, 17, 19, 20, 22, 24, 26, 27 };
        String[] noteNames = { "A3", "B3", "C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5", "G5",
                "A5", "B5", "C6" };

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < noteNames.length; i++) {
            View keyView = inflater.inflate(R.layout.view_piano_key, binding.pianoKeysContainer, false);

            TextView solfegeText = keyView.findViewById(R.id.solfegeText);
            TextView noteNameText = keyView.findViewById(R.id.noteNameText);

            String noteName = noteNames[i];
            noteNameText.setText(noteName);
            solfegeText.setText(getSolfege(noteName));

            int semitones = semitonesFromA3[i];
            double freq = 220.0 * Math.pow(2.0, semitones / 12.0);

            keyView.setTag(freq); // Store frequency in tag

            // Touch listener for manual play
            keyView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    playNote(freq, keyView);
                    v.performClick();
                }
                return true;
            });

            binding.pianoKeysContainer.addView(keyView);
            pianoKeyViews.add(keyView);
        }

        // Sync keys with overlay after layout
        binding.pianoKeysContainer.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        updateOverlayKeys();
                        // Don't remove listener to handle layout changes (e.g. rotation, though locked
                        // to landscape)
                    }
                });

        // Listen for overlay key presses (from hand tracking)
        pianoOverlay.setOnKeyListener(frequency -> {
            // Find the view for this frequency and animate it
            for (View view : pianoKeyViews) {
                double keyFreq = (double) view.getTag();
                if (Math.abs(keyFreq - frequency) < 0.1) {
                    runOnUiThread(() -> animateKeyPress(view));
                    playNote(frequency, null); // Pass null to avoid double animation if we wanted, but playNote handles
                                               // sound
                    break;
                }
            }
        });
    }

    private void updateOverlayKeys() {
        List<PianoOverlayView.Key> overlayKeys = new ArrayList<>();
        int[] location = new int[2];

        for (View view : pianoKeyViews) {
            view.getLocationOnScreen(location);
            float left = location[0];
            float top = location[1];
            float right = left + view.getWidth();
            float bottom = top + view.getHeight();

            // Adjust for overlay coordinate system if needed (usually overlay is
            // match_parent so screen coords work)
            // But if immersive mode hides status bar, getLocationOnScreen returns absolute
            // screen coords.
            // PianoOverlayView is also full screen, so it should match.

            double freq = (double) view.getTag();
            TextView noteText = view.findViewById(R.id.noteNameText);
            String label = noteText.getText().toString();

            overlayKeys
                    .add(new PianoOverlayView.Key(new android.graphics.RectF(left, top, right, bottom), freq, label));
        }
        pianoOverlay.setKeys(overlayKeys);
    }

    private void playNote(double frequency, View keyView) {
        audioEngine.playNote(frequency);

        // Haptic feedback
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }

        if (keyView != null) {
            animateKeyPress(keyView);
        }

        // Strict note checking in Play by Sheet mode
        if (isPlayBySheetMode && sheetMusicEngine != null && !isDemoMode) {
            // Don't accept input during post-completion cooldown or demo mode
            if (isPostCompletionCooldown) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            Note expectedNote = sheetMusicEngine.getCurrentNote();

            if (expectedNote != null) {
                boolean isCorrectNote = Math.abs(frequency - expectedNote.getFrequency()) < 1.0;

                // If this is an incorrect key, mark it
                if (!isCorrectNote) {
                    hasIncorrectKeyPress = true;
                    lastKeyPressTime = currentTime;
                    // Provide "try again" feedback
                    handleTryAgainPrompt();
                } else {
                    // This is the correct key
                    // Check if any incorrect key was pressed recently
                    if (currentTime - lastKeyPressTime > KEY_PRESS_GRACE_PERIOD_MS) {
                        // No recent key presses, reset the flag
                        hasIncorrectKeyPress = false;
                    }

                    // Only accept if no incorrect keys were pressed
                    if (!hasIncorrectKeyPress) {
                        sheetMusicEngine.checkNote(frequency);
                        // Reset for next note
                        hasIncorrectKeyPress = false;
                        lastKeyPressTime = 0;
                    } else {
                        // Incorrect key was pressed, don't count this
                        // Reset the flag after grace period
                        hasIncorrectKeyPress = false;
                        lastKeyPressTime = currentTime;
                    }
                }
            }
        }
    }

    private void animateKeyPress(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(50)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(50).start())
                .start();

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            // Fix: Don't read current color as it might be already highlighted
            // int originalColor = card.getCardBackgroundColor().getDefaultColor();
            int highlightColor = ContextCompat.getColor(this, R.color.md_theme_light_primaryContainer);
            int defaultColor = ContextCompat.getColor(this, R.color.md_theme_light_surface); // Or Color.WHITE

            card.setCardBackgroundColor(highlightColor);
            view.postDelayed(() -> card.setCardBackgroundColor(defaultColor), 150);
        }
    }

    private void setupSheetPreview() {
        LayoutInflater inflater = LayoutInflater.from(this);
        sheetPreviewView = inflater.inflate(R.layout.view_sheet_preview, binding.sheetPreviewContainer, false);
        binding.sheetPreviewContainer.addView(sheetPreviewView);
        binding.sheetPreviewContainer.setVisibility(View.GONE); // Hidden by default

        previewSongTitle = sheetPreviewView.findViewById(R.id.previewSongTitle);
        noteStreamRecyclerView = sheetPreviewView.findViewById(R.id.noteStreamRecyclerView);
        progressBar = sheetPreviewView.findViewById(R.id.progressBar);

        sheetPreviewAdapter = new SheetPreviewAdapter();
        noteStreamRecyclerView.setAdapter(sheetPreviewAdapter);

        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(noteStreamRecyclerView);

        sheetMusicEngine.setListener(new SheetMusicEngine.SheetMusicListener() {
            @Override
            public void onNoteCorrect(Note note, int progress, int total) {
                runOnUiThread(() -> {
                    updateSheetPreviewUI();
                    // Highlight key green?
                });
            }

            @Override
            public void onNoteIncorrect(Note expected, double played) {
                runOnUiThread(() -> {
                    handleTryAgainPrompt();
                    // Highlight key red?
                });
            }

            @Override
            public void onSongComplete() {
                runOnUiThread(() -> {
                    speak("Amazing! You're a star!");
                    showSnackbar("🎉 Song Complete! Timing: " + timingAnalyzer.getAccuracyPercentage() + "%",
                            Snackbar.LENGTH_LONG);

                    // Enable cooldown to prevent immediate replay
                    isPostCompletionCooldown = true;

                    // Reset after cooldown period
                    new android.os.Handler().postDelayed(() -> {
                        sheetMusicEngine.reset();
                        // Explicitly scroll to first note
                        noteStreamRecyclerView.scrollToPosition(0);
                        updateSheetPreviewUI();
                        isPostCompletionCooldown = false;
                    }, POST_COMPLETION_COOLDOWN_MS);
                });
            }
        });

        sheetPreviewAdapter.setOnNoteClickListener(position -> {
            runOnUiThread(() -> {
                sheetMusicEngine.setProgress(position);
                updateSheetPreviewUI();
                showSnackbar("Rewound to note " + (position + 1));
            });
        });
    }

    private void updateSheetPreviewUI() {
        updateSheetPreviewUI(true);
    }

    private void updateSheetPreviewUI(boolean shouldScroll) {
        if (sheetMusicEngine.getCurrentSong() == null)
            return;

        String currentTitle = sheetMusicEngine.getCurrentSong().getTitle();
        boolean songChanged = !currentTitle.equals(lastSongTitle);

        if (songChanged) {
            lastSongTitle = currentTitle;
            previewSongTitle.setText(currentTitle);
            // Update notes list when song changes
            List<Note> allNotes = sheetMusicEngine.getCurrentSong().getNotes(sheetMusicEngine.isUseLongVersion());
            sheetPreviewAdapter.setNotes(allNotes);
            // Scroll to beginning on song change
            noteStreamRecyclerView.scrollToPosition(0);
        }

        int currentProgress = sheetMusicEngine.getProgress();
        sheetPreviewAdapter.setCurrentNoteIndex(currentProgress);

        // Auto-scroll to center the highlighted note after playing
        if (shouldScroll && noteStreamRecyclerView.getLayoutManager() != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) noteStreamRecyclerView.getLayoutManager();

            // Use custom smooth scroller that centers the item
            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(this) {
                @Override
                protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                    // Slow down the scroll speed (higher value = slower)
                    // Default is 25f, we use 100f for smoother, slower scrolling
                    return 100f / displayMetrics.densityDpi;
                }

                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    // Calculate offset to center the item
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
            };
            smoothScroller.setTargetPosition(currentProgress);
            layoutManager.startSmoothScroll(smoothScroller);
        }

        // Update progress
        int total = sheetMusicEngine.getCurrentSong().getTotalNotes(sheetMusicEngine.isUseLongVersion());
        float progressRatio = (float) currentProgress / total;
        progressBar.setProgress((int) (progressRatio * 100));

        // Update fingering on overlay
        Note nextNote = sheetMusicEngine.getCurrentNote();
        if (nextNote != null) {
            int displayFinger = toDisplayFinger(nextNote.getRecommendedFinger());
            if (displayFinger > 0) {
                pianoOverlay.setRecommendedFinger(displayFinger, nextNote.getFrequency());
            } else {
                pianoOverlay.clearRecommendedFinger();
            }
        } else {
            pianoOverlay.clearRecommendedFinger();
        }
    }

    private void setupToolbar() {
        binding.verticalToolbar.playDemoButton.setEnabled(false);

        binding.verticalToolbar.modeSwitchButton.setOnClickListener(v -> {
            isPlayBySheetMode = !isPlayBySheetMode;
            
            AutoTransition autoTransition = new AutoTransition();
            autoTransition.setDuration(150);
            TransitionManager.beginDelayedTransition(binding.getRoot(), autoTransition);
            
            if (isPlayBySheetMode) {
                binding.sheetPreviewContainer.setVisibility(View.VISIBLE);
                sheetMusicEngine.reset();
                timingAnalyzer.startSong();
                updateSheetPreviewUI();
                showSnackbar("Play by sheet mode");
                binding.verticalToolbar.playDemoButton.setEnabled(true);
            } else {
                binding.sheetPreviewContainer.setVisibility(View.GONE);
                timingAnalyzer.reset();
                pianoOverlay.clearRecommendedFinger();
                showSnackbar("Free play mode");
                binding.verticalToolbar.playDemoButton.setEnabled(false);
                if (sheetMusicEngine.isDemoPlaying()) {
                    sheetMusicEngine.stopDemo();
                    binding.verticalToolbar.playDemoButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_play_arrow));
                }
            }
        });

        binding.verticalToolbar.songSelectionButton.setOnClickListener(v -> showSongSelectorSideSheet());

        binding.verticalToolbar.playDemoButton.setOnClickListener(v -> {
            if (sheetMusicEngine != null && sheetMusicEngine.getCurrentSong() != null) {
                
                if (sheetMusicEngine.isDemoPlaying()) {
                    sheetMusicEngine.stopDemo();
                    binding.verticalToolbar.playDemoButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_play_arrow));
                    showSnackbar("Demo stopped");
                    isDemoMode = false;
                } else {
                    showSnackbar("Playing demo...");
                    isDemoMode = true;
                    binding.verticalToolbar.playDemoButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_stop));

                    sheetMusicEngine.playDemo(new SheetMusicEngine.AutoPlayCallback() {
                        @Override
                        public void onPlayNote(double frequency) {
                            runOnUiThread(() -> {
                                playNote(frequency, null);
                                for (View view : pianoKeyViews) {
                                    if (Math.abs((double) view.getTag() - frequency) < 0.1) {
                                        animateKeyPress(view);
                                        break;
                                    }
                                }
                                updateSheetPreviewUI(true);
                            });
                        }

                        @Override
                        public void onAutoDemoComplete() {
                            runOnUiThread(() -> {
                                isDemoMode = false; // Disable demo mode
                                binding.verticalToolbar.playDemoButton.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_play_arrow));
                                showSnackbar("Demo complete!");

                                new android.os.Handler().postDelayed(() -> {
                                    sheetMusicEngine.reset();
                                    noteStreamRecyclerView.scrollToPosition(0);
                                    updateSheetPreviewUI();
                                }, POST_COMPLETION_COOLDOWN_MS);
                            });
                        }
                    });
                }
            } else {
                showSnackbar("Select a song first!");
            }
        });
        
        binding.verticalToolbar.voiceCommandButton.setOnClickListener(v -> {
            Log.d(TAG, "Voice command button clicked");
            startVoiceRecognition();
        });
        
        binding.verticalToolbar.voiceCommandButton.setVisibility(View.VISIBLE);
        binding.verticalToolbar.voiceCommandButton.setEnabled(true);
        
        binding.verticalToolbar.chatbotButton.setOnClickListener(v -> {
            Log.d(TAG, "Chatbot button clicked");
            showChatbotSideSheet();
        });
        
        binding.verticalToolbar.chatbotButton.setVisibility(View.VISIBLE);
        binding.verticalToolbar.chatbotButton.setEnabled(true);
    }

    private void showSongSelectorSideSheet() {
        SideSheetDialog sideSheetDialog = new SideSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_side_sheet_song_selection, null);
        sideSheetDialog.setContentView(sheetView);

        RecyclerView favoritesCarousel = sheetView.findViewById(R.id.favoritesCarousel);
        RecyclerView songList = sheetView.findViewById(R.id.songList);

        SongCardAdapter favoritesAdapter = new SongCardAdapter(position -> {
            sheetMusicEngine.loadSong(position);
            sheetMusicEngine.reset();
            updateSheetPreviewUI();
            sideSheetDialog.dismiss();
            showSnackbar("Song selected!");
            
            // Auto-switch to Play by Sheet mode if not already
            if (!isPlayBySheetMode) {
                binding.verticalToolbar.modeSwitchButton.performClick();
            }
        });
        favoritesAdapter.setSongs(sheetMusicEngine.getSongLibrary());
        favoritesCarousel.setAdapter(favoritesAdapter);

        SongAdapter listAdapter = new SongAdapter(position -> {
            sheetMusicEngine.loadSong(position);
            sheetMusicEngine.reset();
            updateSheetPreviewUI();
            sideSheetDialog.dismiss();
            showSnackbar("Song selected!");
            
            // Auto-switch to Play by Sheet mode if not already
            if (!isPlayBySheetMode) {
                binding.verticalToolbar.modeSwitchButton.performClick();
            }
        });
        listAdapter.setSongs(sheetMusicEngine.getSongLibrary());
        songList.setAdapter(listAdapter);

        // Setup Add Sheet button (Coming soon feature)
        com.google.android.material.button.MaterialButton addSheetButton = 
            sheetView.findViewById(R.id.addSheetButton);
        addSheetButton.setOnClickListener(v -> {
            showSnackbar("Coming soon! Sheet music import feature is under development.");
            Log.d(TAG, "Add Sheet button clicked - Coming soon feature");
        });

        sideSheetDialog.show();
    }

    private void showChatbotSideSheet() {
        SideSheetDialog sideSheetDialog = new SideSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_side_sheet_chatbot, null);
        sideSheetDialog.setContentView(sheetView);

        com.dstteam.zhuoctopus.airvirtuoso.ncp.ClovaChatbotService chatbotService = 
            new com.dstteam.zhuoctopus.airvirtuoso.ncp.ClovaChatbotService(this);

        RecyclerView chatRecyclerView = sheetView.findViewById(R.id.chatRecyclerView);
        com.google.android.material.textfield.TextInputEditText messageInput = 
            sheetView.findViewById(R.id.messageInput);
        com.google.android.material.button.MaterialButton sendButton = 
            sheetView.findViewById(R.id.sendButton);
        com.google.android.material.button.MaterialButton clearButton = 
            sheetView.findViewById(R.id.clearChatButton);

        com.dstteam.zhuoctopus.airvirtuoso.ui.ChatMessageAdapter adapter = 
            new com.dstteam.zhuoctopus.airvirtuoso.ui.ChatMessageAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(adapter);

        adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
            com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.BOT,
            "Hello! I'm your piano assistant. Ask me anything about piano, music theory, technique, or how to use AirVirtuoso! 🎹"
        ));

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        });

        View.OnClickListener sendMessage = v -> {
            String message = messageInput.getText() != null ? 
                messageInput.getText().toString().trim() : "";
            
            if (message.isEmpty()) {
                return;
            }

            messageInput.setText("");

            adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
                com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.USER,
                message
            ));

            adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
                com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.SYSTEM,
                "Typing..."
            ));

            sendButton.setEnabled(false);

            chatbotService.sendMessage(message, new com.dstteam.zhuoctopus.airvirtuoso.ncp.ClovaChatbotService.ChatCallback() {
                @Override
                public void onSuccess(com.dstteam.zhuoctopus.airvirtuoso.ncp.ClovaChatbotService.ChatResponse response) {
                    runOnUiThread(() -> {
                        adapter.removeSystemMessages();

                        adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
                            com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.BOT,
                            response.message
                        ));

                        sendButton.setEnabled(true);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        adapter.removeSystemMessages();

                        adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
                            com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.SYSTEM,
                            "Error: " + error
                        ));

                        sendButton.setEnabled(true);
                    });
                }
            });
        };

        sendButton.setOnClickListener(sendMessage);

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage.onClick(sendButton);
                return true;
            }
            return false;
        });

        clearButton.setOnClickListener(v -> {
            adapter.clearMessages();
            adapter.addMessage(new com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage(
                com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage.MessageType.BOT,
                "Chat cleared! How can I help you with piano today? 🎹"
            ));
            chatbotService.resetSession();
        });

        sideSheetDialog.setOnDismissListener(dialog -> {
            chatbotService.release();
        });

        sideSheetDialog.show();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .build();

                Preview preview = new Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build();

                binding.viewFinder.setScaleType(androidx.camera.view.PreviewView.ScaleType.FILL_CENTER);
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                android.util.Size analysisSize = new android.util.Size(960, 540);
                ResolutionSelector analysisResolutionSelector = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(analysisSize,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();

                androidx.camera.core.ImageAnalysis imageAnalysis = new androidx.camera.core.ImageAnalysis.Builder()
                        .setResolutionSelector(analysisResolutionSelector)
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (System.currentTimeMillis() % 2000 < 100) {
                        runOnUiThread(() -> checkEnvironmentWarnings(image));
                    }

                    if (handLandmarkerHelper != null) {
                        handLandmarkerHelper.detectLiveStream(image, true);
                    }
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                try {
                    cameraProvider.unbindAll();
                    androidx.camera.core.Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview,
                            imageAnalysis);

                    androidx.camera.core.CameraControl cameraControl = camera.getCameraControl();
                    cameraControl.setExposureCompensationIndex(2);

                    runOnUiThread(() -> {
                        binding.viewFinder.animate()
                            .alpha(1f)
                            .setDuration(500)
                            .start();
                        
                        View startupLoader = findViewById(R.id.startupLoader);
                        if (startupLoader != null) {
                            startupLoader.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction(() -> startupLoader.setVisibility(View.GONE))
                                .start();
                        }
                    });

                } catch (Exception exc) {
                    Log.e(TAG, "Use case binding failed", exc);
                }

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void checkEnvironmentWarnings(androidx.camera.core.ImageProxy image) {
        // ... (Implement warning logic similar to before)
        // For now, let's keep it simple
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (gravitySensor != null) {
                sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (linearAccelerationSensor != null) {
                sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        cameraExecutor.shutdown();
        if (audioEngine != null) {
            audioEngine.release();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void handleTryAgainPrompt() {
        long now = System.currentTimeMillis();
        if (now - lastTryAgainSpeakTime >= TRY_AGAIN_COOLDOWN_MS) {
            speak("Try again", TRY_AGAIN_VOLUME);
            lastTryAgainSpeakTime = now;
        }
    }

    private void speak(String text) {
        speak(text, 1.0f);
    }

    private void speak(String text, float volume) {
        if (textToSpeech != null && isTtsReady) {
            String utteranceId = "tts_" + System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            } else {
                java.util.HashMap<String, String> params = new java.util.HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(volume));
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }
        }
    }

    private int toDisplayFinger(int rawFinger) {
        if (rawFinger < 0)
            return -1;
        if (rawFinger >= 1 && rawFinger <= 5)
            return rawFinger;
        if (rawFinger <= 4)
            return rawFinger + 1; // convert 0-based to 1-based
        return -1;
    }

    private String getSolfege(String noteLabel) {
        if (noteLabel == null || noteLabel.isEmpty())
            return "";
        char note = noteLabel.charAt(0);
        switch (note) {
            case 'C':
                return "Do";
            case 'D':
                return "Re";
            case 'E':
                return "Mi";
            case 'F':
                return "Fa";
            case 'G':
                return "Sol";
            case 'A':
                return "La";
            case 'B':
                return "Si";
            default:
                return "";
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.pianoKeysContainer)
                .setAnimationMode(com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                .show();
    }

    private void showSnackbar(String message, int duration) {
        Snackbar.make(binding.getRoot(), message, duration)
                .setAnchorView(binding.pianoKeysContainer)
                .setAnimationMode(com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_SLIDE)
                .show();
    }

    private void showWarningChip(String message, int iconResId, int backgroundColor) {
        runOnUiThread(() -> {
            // Check if chip with this message already exists
            for (int i = 0; i < warningChipGroup.getChildCount(); i++) {
                View child = warningChipGroup.getChildAt(i);
                if (child instanceof Chip) {
                    Chip existingChip = (Chip) child;
                    if (existingChip.getText().toString().equals(message)) {
                        return; // Already showing this warning
                    }
                }
            }

            // Create new chip
            Chip chip = new Chip(this);
            chip.setText(message);
            chip.setChipIcon(getDrawable(iconResId));
            chip.setChipBackgroundColorResource(backgroundColor);
            chip.setTextColor(getColor(android.R.color.white));
            chip.setCloseIconVisible(false); // No close button
            // chip.setOnCloseIconClickListener(v -> warningChipGroup.removeView(chip));

            warningChipGroup.addView(chip);

            // Auto-dismiss after 2 seconds
            chip.postDelayed(() -> {
                // Ensure chip is still in the group before removing to avoid crashes or weird states
                if (chip.getParent() == warningChipGroup) {
                    warningChipGroup.removeView(chip);
                }
            }, 2000);
        });
    }

    private void clearWarningChips() {
        runOnUiThread(() -> warningChipGroup.removeAllViews());
    }
    
    // ==================== Voice Commands (Native Android STT) ====================
    
    /**
     * Start voice recognition for voice commands
     */
    public void startVoiceRecognition() {
        Log.d(TAG, "startVoiceRecognition() called");
        
        // Check if speech recognition is available
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available on this device");
            showSnackbar("Voice commands not available on this device");
            return;
        }
        
        // Initialize if not already initialized
        if (speechRecognizer == null) {
            Log.d(TAG, "Initializing SpeechRecognizer");
            speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            if (speechRecognizer == null) {
                Log.e(TAG, "Failed to create SpeechRecognizer");
                showSnackbar("Failed to initialize voice recognition");
                return;
            }
            speechRecognizer.setRecognitionListener(new VoiceCommandListener());
        }
        
        if (isListening) {
            Log.d(TAG, "Already listening, stopping current session");
            stopVoiceRecognition();
            return;
        }
        
        // Check and request permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "RECORD_AUDIO permission not granted, requesting permission...");
            showSnackbar("Requesting microphone permission...");
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        
        Log.d(TAG, "Permission granted, starting voice recognition");
        
        // Start listening
        try {
            android.content.Intent intent = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            
            isListening = true;
            speechRecognizer.startListening(intent);
            showSnackbar("🎤 Listening... Speak your command");
            Log.d(TAG, "Voice recognition started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting voice recognition", e);
            isListening = false;
            showSnackbar("Voice recognition error: " + e.getMessage());
        }
    }
    
    /**
     * Stop voice recognition
     */
    public void stopVoiceRecognition() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }
    
    /**
     * Process voice command and execute action
     */
    private void processVoiceCommand(String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        
        String lowerCommand = command.toLowerCase().trim();
        Log.d(TAG, "Processing voice command: " + lowerCommand);
        
        // Voice command patterns
        if (lowerCommand.contains("play demo") || lowerCommand.contains("demo")) {
            // Trigger play demo button
            if (sheetMusicEngine != null && sheetMusicEngine.getCurrentSong() != null) {
                binding.verticalToolbar.playDemoButton.performClick();
                speak("Playing demo");
            } else {
                speak("Please select a song first");
            }
        } else if (lowerCommand.contains("reset") || lowerCommand.contains("restart")) {
            // Reset current song
            if (sheetMusicEngine != null) {
                sheetMusicEngine.reset();
                updateSheetPreviewUI();
                speak("Song reset");
            }
        } else if (lowerCommand.contains("next song") || lowerCommand.contains("next")) {
            // Select next song (if available)
            List<Song> songs = sheetMusicEngine.getSongLibrary();
            if (!songs.isEmpty()) {
                int currentIndex = songs.indexOf(sheetMusicEngine.getCurrentSong());
                int nextIndex = (currentIndex + 1) % songs.size();
                sheetMusicEngine.loadSong(nextIndex);
                sheetMusicEngine.reset();
                updateSheetPreviewUI();
                speak("Loaded " + songs.get(nextIndex).getTitle());
            }
        } else if (lowerCommand.contains("previous song") || lowerCommand.contains("previous") || lowerCommand.contains("back")) {
            // Select previous song
            List<Song> songs = sheetMusicEngine.getSongLibrary();
            if (!songs.isEmpty()) {
                int currentIndex = songs.indexOf(sheetMusicEngine.getCurrentSong());
                int prevIndex = (currentIndex - 1 + songs.size()) % songs.size();
                sheetMusicEngine.loadSong(prevIndex);
                sheetMusicEngine.reset();
                updateSheetPreviewUI();
                speak("Loaded " + songs.get(prevIndex).getTitle());
            }
        } else if (lowerCommand.contains("select song") || lowerCommand.contains("choose song")) {
            // Open song selector
            showSongSelectorSideSheet();
            speak("Selecting song");
        } else if (lowerCommand.contains("mode") || lowerCommand.contains("switch")) {
            // Toggle play mode
            binding.verticalToolbar.modeSwitchButton.performClick();
        } else {
            // Unknown command
            speak("Command not recognized. Try: play demo, reset, next song");
        }
    }
    
    /**
     * Voice Recognition Listener
     */
    private class VoiceCommandListener implements android.speech.RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech");
        }
        
        @Override
        public void onRmsChanged(float rmsdB) {
            // Audio level changed
        }
        
        @Override
        public void onBufferReceived(byte[] buffer) {
            // Audio buffer received
        }
        
        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "End of speech");
            isListening = false;
        }
        
        @Override
        public void onError(int error) {
            isListening = false;
            String errorMessage = "Voice recognition error: ";
            switch (error) {
                case android.speech.SpeechRecognizer.ERROR_AUDIO:
                    errorMessage += "Audio error";
                    break;
                case android.speech.SpeechRecognizer.ERROR_CLIENT:
                    errorMessage += "Client error";
                    break;
                case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMessage += "Insufficient permissions";
                    break;
                case android.speech.SpeechRecognizer.ERROR_NETWORK:
                    errorMessage += "Network error";
                    break;
                case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMessage += "Network timeout";
                    break;
                case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage += "No match found";
                    break;
                case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMessage += "Recognizer busy";
                    break;
                case android.speech.SpeechRecognizer.ERROR_SERVER:
                    errorMessage += "Server error";
                    break;
                case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMessage += "Speech timeout";
                    break;
                default:
                    errorMessage += "Unknown error (" + error + ")";
            }
            Log.e(TAG, errorMessage);
            showSnackbar(errorMessage);
        }
        
        @Override
        public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String command = matches.get(0);
                Log.d(TAG, "Voice command recognized: " + command);
                processVoiceCommand(command);
            }
        }
        
        @Override
        public void onPartialResults(Bundle partialResults) {
            // Partial results (not used for commands)
        }
        
        @Override
        public void onEvent(int eventType, Bundle params) {
            // Event occurred
        }
    }
}

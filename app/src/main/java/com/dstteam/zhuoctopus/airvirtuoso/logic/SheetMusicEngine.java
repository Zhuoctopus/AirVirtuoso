package com.dstteam.zhuoctopus.airvirtuoso.logic;

import com.dstteam.zhuoctopus.airvirtuoso.model.Note;
import com.dstteam.zhuoctopus.airvirtuoso.model.Song;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SheetMusicEngine {
    private Song currentSong;
    private int currentNoteIndex = 0;
    private SheetMusicListener listener;
    private List<Song> songLibrary = new ArrayList<>();
    private boolean useLongVersion = false; // Default to short version
    private boolean isDemoPlaying = false;
    private Thread demoThread;

    public interface SheetMusicListener {
        void onNoteCorrect(Note note, int progress, int total);

        void onNoteIncorrect(Note expected, double played);

        void onSongComplete();
    }

    public interface AutoPlayCallback {
        void onPlayNote(double frequency);

        void onAutoDemoComplete();
    }

    public SheetMusicEngine() {
        // Initialize song library
        loadSongLibrary();
        // Set first song as default
        if (!songLibrary.isEmpty()) {
            currentSong = songLibrary.get(0);
        }
    }

    public void setListener(SheetMusicListener listener) {
        this.listener = listener;
    }

    private void loadSongLibrary() {
        // Song 1: Twinkle Twinkle Little Star
        // Quarter note = 750ms, Half note = 1500ms (slowed down tempo)
        // Fingering: 0=pinky, 1=ring, 2=middle, 3=index, 4=thumb
        List<Note> twinkleShort = Arrays.asList(
                new Note("C", 4, 261.63, 750, 4), // Twin- (thumb)
                new Note("C", 4, 261.63, 750, 4), // kle (thumb)
                new Note("G", 4, 392.00, 750, 0), // twin- (pinky)
                new Note("G", 4, 392.00, 750, 0), // kle (pinky)
                new Note("A", 4, 440.00, 750, 0), // lit- (pinky)
                new Note("A", 4, 440.00, 750, 0), // tle (pinky)
                new Note("G", 4, 392.00, 1500, 0), // star (pinky, half note)
                new Note("F", 4, 349.23, 750, 1), // How (ring)
                new Note("F", 4, 349.23, 750, 1), // I (ring)
                new Note("E", 4, 329.63, 750, 2), // won- (middle)
                new Note("E", 4, 329.63, 750, 2), // der (middle)
                new Note("D", 4, 293.66, 750, 3), // what (index)
                new Note("D", 4, 293.66, 750, 3), // you (index)
                new Note("C", 4, 261.63, 1500, 4) // are (thumb, half note)
        );

        List<Note> twinkleLong = Arrays.asList(
                // Verse 1
                new Note("C", 4, 261.63, 750, 4), new Note("C", 4, 261.63, 750, 4),
                new Note("G", 4, 392.00, 750, 0), new Note("G", 4, 392.00, 750, 0),
                new Note("A", 4, 440.00, 750, 0), new Note("A", 4, 440.00, 750, 0),
                new Note("G", 4, 392.00, 1500, 0),
                new Note("F", 4, 349.23, 750, 1), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 750, 3), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4),
                // Verse 2
                new Note("G", 4, 392.00, 750, 0), new Note("G", 4, 392.00, 750, 0),
                new Note("F", 4, 349.23, 750, 1), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 1500, 3),
                new Note("G", 4, 392.00, 750, 0), new Note("G", 4, 392.00, 750, 0),
                new Note("F", 4, 349.23, 750, 1), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 1500, 3),
                // Repeat Verse 1
                new Note("C", 4, 261.63, 750, 4), new Note("C", 4, 261.63, 750, 4),
                new Note("G", 4, 392.00, 750, 0), new Note("G", 4, 392.00, 750, 0),
                new Note("A", 4, 440.00, 750, 0), new Note("A", 4, 440.00, 750, 0),
                new Note("G", 4, 392.00, 1500, 0),
                new Note("F", 4, 349.23, 750, 1), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 750, 3), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4));

        // Add both Short and Long versions as separate entries
        songLibrary.add(new Song("Twinkle Twinkle Little Star (Short)", twinkleShort, twinkleShort)); // Use short notes for both
        songLibrary.add(new Song("Twinkle Twinkle Little Star (Long)", twinkleLong, twinkleLong));   // Use long notes for both

        // Song 2: Mary Had a Little Lamb
        List<Note> maryShort = Arrays.asList(
                new Note("C", 4, 261.63, 750), // had
                new Note("D", 4, 293.66, 750), // a
                new Note("E", 4, 329.63, 750), // lit-
                new Note("E", 4, 329.63, 750), // tle
                new Note("E", 4, 329.63, 1500), // lamb (half note)
                new Note("D", 4, 293.66, 750), // lit-
                new Note("D", 4, 293.66, 750), // tle
                new Note("D", 4, 293.66, 1500), // lamb (half note)
                new Note("E", 4, 329.63, 750), // lit-
                new Note("G", 4, 392.00, 750), // tle
                new Note("G", 4, 392.00, 1500) // lamb (half note)
        );

        List<Note> maryLong = Arrays.asList(
                // Verse 1
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 750, 4), new Note("D", 4, 293.66, 750, 3),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("E", 4, 329.63, 1500, 2),
                new Note("D", 4, 293.66, 750, 3), new Note("D", 4, 293.66, 750, 3),
                new Note("D", 4, 293.66, 1500, 3),
                new Note("E", 4, 329.63, 750, 2), new Note("G", 4, 392.00, 750, 0),
                new Note("G", 4, 392.00, 1500, 0),
                // Verse 2
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 750, 4), new Note("D", 4, 293.66, 750, 3),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 750, 3), new Note("D", 4, 293.66, 750, 3),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4));

        songLibrary.add(new Song("Mary Had a Little Lamb (Short)", maryShort, maryShort));
        songLibrary.add(new Song("Mary Had a Little Lamb (Long)", maryLong, maryLong));

        // Song 3: Hot Cross Buns
        List<Note> hotCrossShort = Arrays.asList(
                new Note("E", 4, 329.63, 750, 2), // Hot (middle)
                new Note("D", 4, 293.66, 750, 3), // cross (index)
                new Note("C", 4, 261.63, 1500, 4), // buns (thumb, half note)
                new Note("E", 4, 329.63, 750, 2), // Hot (middle)
                new Note("D", 4, 293.66, 750, 3), // cross (index)
                new Note("C", 4, 261.63, 1500, 4), // buns (thumb, half note)
                new Note("C", 4, 261.63, 375, 4), // One (thumb)
                new Note("C", 4, 261.63, 375, 4), // a (thumb)
                new Note("C", 4, 261.63, 375, 4), // pen- (thumb)
                new Note("C", 4, 261.63, 375, 4), // ny (thumb)
                new Note("D", 4, 293.66, 375, 3), // Two (index)
                new Note("D", 4, 293.66, 375, 3), // a (index)
                new Note("D", 4, 293.66, 375, 3), // pen- (index)
                new Note("D", 4, 293.66, 375, 3), // ny (index)
                new Note("E", 4, 329.63, 750, 2), // Hot (middle)
                new Note("D", 4, 293.66, 750, 3), // cross (index)
                new Note("C", 4, 261.63, 1500, 4) // buns (thumb, half note)
        );

        List<Note> hotCrossLong = Arrays.asList(
                // Verse 1
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4),
                new Note("C", 4, 261.63, 375, 4), new Note("C", 4, 261.63, 375, 4),
                new Note("C", 4, 261.63, 375, 4), new Note("C", 4, 261.63, 375, 4),
                new Note("D", 4, 293.66, 375, 3), new Note("D", 4, 293.66, 375, 3),
                new Note("D", 4, 293.66, 375, 3), new Note("D", 4, 293.66, 375, 3),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4),
                // Repeat
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 1500, 4));
        
        songLibrary.add(new Song("Hot Cross Buns (Short)", hotCrossShort, hotCrossShort));
        songLibrary.add(new Song("Hot Cross Buns (Long)", hotCrossLong, hotCrossLong));

        // Song 4: Ode to Joy (simplified)
        List<Note> odeShort = Arrays.asList(
                new Note("E", 4, 329.63, 750, 2),
                new Note("E", 4, 329.63, 750, 2),
                new Note("F", 4, 349.23, 750, 1),
                new Note("G", 4, 392.00, 750, 0),
                new Note("G", 4, 392.00, 750, 0),
                new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 750, 4),
                new Note("C", 4, 261.63, 750, 4),
                new Note("D", 4, 293.66, 750, 3),
                new Note("E", 4, 329.63, 750, 2),
                new Note("E", 4, 329.63, 1125, 2),
                new Note("D", 4, 293.66, 375, 3),
                new Note("D", 4, 293.66, 1500, 3));

        List<Note> odeLong = Arrays.asList(
                // Part 1
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("F", 4, 349.23, 750, 1), new Note("G", 4, 392.00, 750, 0),
                new Note("G", 4, 392.00, 750, 0), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 750, 4), new Note("C", 4, 261.63, 750, 4),
                new Note("D", 4, 293.66, 750, 3), new Note("E", 4, 329.63, 750, 2),
                new Note("E", 4, 329.63, 1125, 2), new Note("D", 4, 293.66, 375, 3),
                new Note("D", 4, 293.66, 1500, 3),
                // Part 2
                new Note("E", 4, 329.63, 750, 2), new Note("E", 4, 329.63, 750, 2),
                new Note("F", 4, 349.23, 750, 1), new Note("G", 4, 392.00, 750, 0),
                new Note("G", 4, 392.00, 750, 0), new Note("F", 4, 349.23, 750, 1),
                new Note("E", 4, 329.63, 750, 2), new Note("D", 4, 293.66, 750, 3),
                new Note("C", 4, 261.63, 750, 4), new Note("C", 4, 261.63, 750, 4),
                new Note("D", 4, 293.66, 750, 3), new Note("E", 4, 329.63, 750, 2),
                new Note("D", 4, 293.66, 1125, 3), new Note("C", 4, 261.63, 375, 4),
                new Note("C", 4, 261.63, 1500, 4));
        
        songLibrary.add(new Song("Ode to Joy (Short)", odeShort, odeShort));
        songLibrary.add(new Song("Ode to Joy (Long)", odeLong, odeLong));

        // Placeholder for future additions (Samsung theme to be reintroduced later)
    }

    public List<Song> getSongLibrary() {
        return new ArrayList<>(songLibrary);
    }

    public void setUseLongVersion(boolean useLongVersion) {
        this.useLongVersion = useLongVersion;
    }

    public boolean isUseLongVersion() {
        return useLongVersion;
    }

    public void loadSong(int index) {
        if (index >= 0 && index < songLibrary.size()) {
            currentSong = songLibrary.get(index);
            currentNoteIndex = 0;
        }
    }

    public void startSong(Song song) {
        this.currentSong = song;
        this.currentNoteIndex = 0;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public Note getCurrentNote() {
        if (currentSong == null || currentNoteIndex >= currentSong.getNotes(useLongVersion).size()) {
            return null;
        }
        return currentSong.getNotes(useLongVersion).get(currentNoteIndex);
    }

    public List<Note> getUpcomingNotes(int count) {
        if (currentSong == null)
            return new ArrayList<>();

        List<Note> upcoming = new ArrayList<>();
        int endIndex = Math.min(currentNoteIndex + count, currentSong.getNotes(useLongVersion).size());
        for (int i = currentNoteIndex; i < endIndex; i++) {
            upcoming.add(currentSong.getNotes(useLongVersion).get(i));
        }
        return upcoming;
    }

    public boolean checkNote(double playedFrequency) {
        Note expectedNote = getCurrentNote();
        if (expectedNote == null) {
            return false;
        }

        // Allow small tolerance for floating point comparison
        boolean isCorrect = Math.abs(playedFrequency - expectedNote.getFrequency()) < 1.0;

        if (isCorrect) {
            currentNoteIndex++;
            if (listener != null) {
                listener.onNoteCorrect(expectedNote, currentNoteIndex, currentSong.getTotalNotes(useLongVersion));

                if (currentNoteIndex >= currentSong.getTotalNotes(useLongVersion)) {
                    listener.onSongComplete();
                }
            }
        } else {
            if (listener != null) {
                listener.onNoteIncorrect(expectedNote, playedFrequency);
            }
        }

        return isCorrect;
    }

    public void reset() {
        currentNoteIndex = 0;
        stopDemo();
    }

    public int getProgress() {
        return currentNoteIndex;
    }

    public void setProgress(int index) {
        if (currentSong != null && index >= 0 && index < currentSong.getTotalNotes(useLongVersion)) {
            this.currentNoteIndex = index;
        }
    }

    public boolean isComplete() {
        return currentSong != null && currentNoteIndex >= currentSong.getTotalNotes(useLongVersion);
    }

    public boolean isDemoPlaying() {
        return isDemoPlaying;
    }

    public void stopDemo() {
        if (demoThread != null && demoThread.isAlive()) {
            demoThread.interrupt();
        }
        isDemoPlaying = false;
        demoThread = null;
    }

    // Auto-play demo: plays through the song automatically
    public void playDemo(AutoPlayCallback callback) {
        if (currentSong == null || callback == null || isDemoPlaying)
            return;

        isDemoPlaying = true;
        demoThread = new Thread(() -> {
            currentNoteIndex = 0; // Reset progress at the start
            List<Note> notes = currentSong.getNotes(useLongVersion);
            try {
                for (int i = 0; i < notes.size(); i++) {
                    if (Thread.interrupted()) throw new InterruptedException();
                    
                    Note note = notes.get(i);
                    callback.onPlayNote(note.getFrequency());
                    currentNoteIndex = i + 1; // Update progress
                    Thread.sleep(note.getDurationMs()); // Use note's duration
                }
                // Only call complete if we finished naturally
                isDemoPlaying = false;
                callback.onAutoDemoComplete();
            } catch (InterruptedException e) {
                // Demo stopped
                isDemoPlaying = false;
            }
        });
        demoThread.start();
    }
}

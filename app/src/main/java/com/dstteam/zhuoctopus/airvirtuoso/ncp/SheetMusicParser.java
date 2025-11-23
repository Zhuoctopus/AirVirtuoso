package com.dstteam.zhuoctopus.airvirtuoso.ncp;

import android.util.Log;

import com.dstteam.zhuoctopus.airvirtuoso.model.Note;
import com.dstteam.zhuoctopus.airvirtuoso.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser to convert OCR text results into musical notes
 * Supports multiple notation formats including ABC notation and simple note names
 */
public class SheetMusicParser {
    private static final String TAG = "SheetMusicParser";
    
    // Note frequencies (Hz) for octaves 3-6
    private static final double[] NOTE_FREQUENCIES = {
        261.63,  // C4
        277.18,  // C#4/Db4
        293.66,  // D4
        311.13,  // D#4/Eb4
        329.63,  // E4
        349.23,  // F4
        369.99,  // F#4/Gb4
        392.00,  // G4
        415.30,  // G#4/Ab4
        440.00,  // A4
        466.16,  // A#4/Bb4
        493.88   // B4
    };
    
    /**
     * Parse OCR text into a Song object
     * 
     * @param ocrText Text extracted from OCR
     * @param songTitle Title of the song
     * @return Parsed Song object, or null if parsing fails
     */
    public static Song parseOcrText(String ocrText, String songTitle) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            Log.w(TAG, "Empty OCR text");
            return null;
        }
        
        // Try different parsing strategies
        List<Note> notes = null;
        
        // Strategy 1: ABC notation (e.g., "C D E F G A B")
        notes = parseAbcNotation(ocrText);
        
        // Strategy 2: Simple note names with octaves (e.g., "C4 D4 E4")
        if (notes == null || notes.isEmpty()) {
            notes = parseNoteNames(ocrText);
        }
        
        // Strategy 3: Solfege notation (e.g., "Do Re Mi")
        if (notes == null || notes.isEmpty()) {
            notes = parseSolfegeNotation(ocrText);
        }
        
        if (notes == null || notes.isEmpty()) {
            Log.w(TAG, "Failed to parse any notes from OCR text: " + ocrText);
            return null;
        }
        
        // Create song with default duration (750ms per note)
        List<Note> notesWithDuration = new ArrayList<>();
        for (Note note : notes) {
            notesWithDuration.add(new Note(
                note.getName(),
                note.getOctave(),
                note.getFrequency(),
                750, // Default duration
                note.getRecommendedFinger()
            ));
        }
        
        return new Song(songTitle, notesWithDuration, notesWithDuration);
    }
    
    /**
     * Parse ABC notation: "C D E F G A B" or "C4 D4 E4"
     */
    private static List<Note> parseAbcNotation(String text) {
        List<Note> notes = new ArrayList<>();
        
        // Pattern for note names with optional octave: C, C4, C#4, Db4, etc.
        Pattern pattern = Pattern.compile("([A-G][#b]?)(\\d)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String noteName = matcher.group(1).toUpperCase();
            String octaveStr = matcher.group(2);
            int octave = octaveStr != null ? Integer.parseInt(octaveStr) : 4; // Default to octave 4
            
            Note note = createNote(noteName, octave);
            if (note != null) {
                notes.add(note);
            }
        }
        
        return notes;
    }
    
    /**
     * Parse simple note names: "C D E F G A B"
     */
    private static List<Note> parseNoteNames(String text) {
        List<Note> notes = new ArrayList<>();
        
        // Simple pattern for single letter notes
        Pattern pattern = Pattern.compile("\\b([A-G])\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        int octave = 4; // Default octave
        while (matcher.find()) {
            String noteName = matcher.group(1).toUpperCase();
            Note note = createNote(noteName, octave);
            if (note != null) {
                notes.add(note);
            }
        }
        
        return notes;
    }
    
    /**
     * Parse solfege notation: "Do Re Mi Fa Sol La Si"
     */
    private static List<Note> parseSolfegeNotation(String text) {
        List<Note> notes = new ArrayList<>();
        
        // Map solfege to note names
        String[] solfege = {"DO", "RE", "MI", "FA", "SOL", "LA", "SI"};
        String[] noteNames = {"C", "D", "E", "F", "G", "A", "B"};
        
        Pattern pattern = Pattern.compile("\\b(DO|RE|MI|FA|SOL|LA|SI)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        int octave = 4; // Default octave
        while (matcher.find()) {
            String solfegeName = matcher.group(1).toUpperCase();
            for (int i = 0; i < solfege.length; i++) {
                if (solfege[i].equals(solfegeName)) {
                    Note note = createNote(noteNames[i], octave);
                    if (note != null) {
                        notes.add(note);
                    }
                    break;
                }
            }
        }
        
        return notes;
    }
    
    /**
     * Create a Note object from note name and octave
     */
    private static Note createNote(String noteName, int octave) {
        // Map note names to semitones from C
        int semitone = 0;
        
        switch (noteName.charAt(0)) {
            case 'C':
                semitone = 0;
                break;
            case 'D':
                semitone = 2;
                break;
            case 'E':
                semitone = 4;
                break;
            case 'F':
                semitone = 5;
                break;
            case 'G':
                semitone = 7;
                break;
            case 'A':
                semitone = 9;
                break;
            case 'B':
                semitone = 11;
                break;
            default:
                return null;
        }
        
        // Handle sharps and flats
        if (noteName.length() > 1) {
            if (noteName.charAt(1) == '#') {
                semitone++;
            } else if (noteName.charAt(1) == 'b') {
                semitone--;
            }
        }
        
        // Calculate frequency
        int semitonesFromC4 = (octave - 4) * 12 + semitone;
        double frequency = NOTE_FREQUENCIES[0] * Math.pow(2, semitonesFromC4 / 12.0);
        
        // Clamp octave to supported range (3-6)
        octave = Math.max(3, Math.min(6, octave));
        
        return new Note(noteName.charAt(0) + "", octave, frequency);
    }
}

package com.dstteam.zhuoctopus.airvirtuoso.model;

import java.util.List;

public class Song {
    private String title;
    private List<Note> shortNotes;
    private List<Note> longNotes;

    public Song(String title, List<Note> shortNotes, List<Note> longNotes) {
        this.title = title;
        this.shortNotes = shortNotes;
        this.longNotes = longNotes;
    }

    public String getTitle() {
        return title;
    }

    public List<Note> getShortNotes() {
        return shortNotes;
    }

    public List<Note> getLongNotes() {
        return longNotes;
    }

    public List<Note> getNotes(boolean useLongVersion) {
        return useLongVersion ? longNotes : shortNotes;
    }

    public int getTotalNotes(boolean useLongVersion) {
        return useLongVersion ? longNotes.size() : shortNotes.size();
    }

    // Legacy methods for backward compatibility
    public List<Note> getNotes() {
        return shortNotes;
    }

    public int getTotalNotes() {
        return shortNotes.size();
    }
}

package com.dstteam.zhuoctopus.airvirtuoso.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dstteam.zhuoctopus.airvirtuoso.R;
import com.dstteam.zhuoctopus.airvirtuoso.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private List<Song> songs = new ArrayList<>();
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public SongAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private TextView titleText;
        private TextView notesCountText;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.song_title);
            notesCountText = itemView.findViewById(R.id.song_notes_count);
        }

        public void bind(Song song, int position) {
            titleText.setText(song.getTitle());
            notesCountText.setText(song.getTotalNotes() + " notes");
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(position);
                }
            });
        }
    }
}

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

public class SongCardAdapter extends RecyclerView.Adapter<SongCardAdapter.SongCardViewHolder> {
    private List<Song> songs = new ArrayList<>();
    private OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public SongCardAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song_card, parent, false);
        return new SongCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongCardViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song, position);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SongCardViewHolder extends RecyclerView.ViewHolder {
        private TextView titleText;
        private TextView artistText;

        public SongCardViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.songTitle);
            artistText = itemView.findViewById(R.id.songArtist);
        }

        public void bind(Song song, int position) {
            titleText.setText(song.getTitle());
            // Use note count as subtitle since we don't have artist
            artistText.setText(song.getTotalNotes() + " notes");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(position);
                }
            });
        }
    }
}

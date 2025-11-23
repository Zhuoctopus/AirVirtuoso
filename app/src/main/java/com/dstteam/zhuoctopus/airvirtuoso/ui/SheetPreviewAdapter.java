package com.dstteam.zhuoctopus.airvirtuoso.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dstteam.zhuoctopus.airvirtuoso.R;
import com.dstteam.zhuoctopus.airvirtuoso.model.Note;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SheetPreviewAdapter extends RecyclerView.Adapter<SheetPreviewAdapter.NoteViewHolder> {

    private List<Note> notes = new ArrayList<>();
    private int currentNoteIndex = -1;
    private OnNoteClickListener onNoteClickListener;

    public interface OnNoteClickListener {
        void onNoteClick(int position);
    }

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.onNoteClickListener = listener;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }

    public void setCurrentNoteIndex(int index) {
        int oldIndex = this.currentNoteIndex;
        this.currentNoteIndex = index;
        if (oldIndex != -1)
            notifyItemChanged(oldIndex);
        if (index != -1)
            notifyItemChanged(index);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sheet_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.noteName.setText(note.getName() + note.getOctave());

        if (position == currentNoteIndex) {
            // Highlight current note - Use colorPrimaryContainer from theme
            int primaryContainer = holder.itemView.getContext().getColor(R.color.md_theme_light_primaryContainer);
            int primary = holder.itemView.getContext().getColor(R.color.md_theme_light_primary);
            int onPrimaryContainer = holder.itemView.getContext().getColor(R.color.md_theme_light_onPrimaryContainer);
            
            holder.cardView.setCardBackgroundColor(primaryContainer);
            holder.cardView.setStrokeWidth(4);
            holder.cardView.setStrokeColor(primary);
            holder.noteName.setTextColor(onPrimaryContainer);
        } else {
            // Normal note - Transparent background, use OnSurface color for text
            int onSurface = holder.itemView.getContext().getColor(R.color.md_theme_light_onSurface);
            int outline = holder.itemView.getContext().getColor(R.color.md_theme_light_outline);
            
            holder.cardView.setCardBackgroundColor(Color.TRANSPARENT);
            holder.cardView.setStrokeWidth(1);
            holder.cardView.setStrokeColor(outline);
            holder.noteName.setTextColor(onSurface);
        }

        // Click listener for rewinding to past notes
        holder.itemView.setOnClickListener(v -> {
            if (onNoteClickListener != null && position < currentNoteIndex) {
                onNoteClickListener.onNoteClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView noteName;
        MaterialCardView cardView;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            noteName = itemView.findViewById(R.id.noteName);
        }
    }
}

package com.dstteam.zhuoctopus.airvirtuoso.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.dstteam.zhuoctopus.airvirtuoso.R;
import com.dstteam.zhuoctopus.airvirtuoso.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying chat messages in RecyclerView
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_BOT = 2;
    private static final int VIEW_TYPE_SYSTEM = 3;
    
    private final List<ChatMessage> messages = new ArrayList<>();
    
    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        switch (message.getType()) {
            case USER:
                return VIEW_TYPE_USER;
            case BOT:
                return VIEW_TYPE_BOT;
            case SYSTEM:
                return VIEW_TYPE_SYSTEM;
            default:
                return VIEW_TYPE_BOT;
        }
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        switch (viewType) {
            case VIEW_TYPE_USER:
                View userView = inflater.inflate(R.layout.item_chat_message_user, parent, false);
                return new UserMessageViewHolder(userView);
            case VIEW_TYPE_BOT:
                View botView = inflater.inflate(R.layout.item_chat_message_bot, parent, false);
                return new BotMessageViewHolder(botView);
            case VIEW_TYPE_SYSTEM:
                View systemView = inflater.inflate(R.layout.item_chat_message_system, parent, false);
                return new SystemMessageViewHolder(systemView);
            default:
                View defaultView = inflater.inflate(R.layout.item_chat_message_bot, parent, false);
                return new BotMessageViewHolder(defaultView);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof BotMessageViewHolder) {
            ((BotMessageViewHolder) holder).bind(message);
        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).bind(message);
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
    
    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }
    
    public void removeSystemMessages() {
        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isSystem()) {
                indicesToRemove.add(i);
            }
        }
        for (int index : indicesToRemove) {
            messages.remove(index);
            notifyItemRemoved(index);
        }
    }
    
    // View Holders
    private static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        
        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
        
        void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }
    
    private static class BotMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        
        BotMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }
        
        void bind(ChatMessage message) {
            messageText.setText(message.getMessage());
        }
    }
    
    private static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final ProgressBar loadingIndicator;
        
        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            loadingIndicator = itemView.findViewById(R.id.loadingIndicator);
        }
        
        void bind(ChatMessage message) {
            String text = message.getMessage();
            
            if (text != null && (text.equals("Typing...") || text.contains("Loading"))) {
                // Show loading indicator for "Typing..." message
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.VISIBLE);
                }
                // Hide text as requested
                messageText.setVisibility(View.GONE);
            } else {
                // Show text for normal system messages (errors, etc.)
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(text);
            }
        }
    }
}


package com.chat.server.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.server.R;
import com.chat.server.model.ChatMessage;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatRoomActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private TextView tvTitle;
    
    private MessageAdapter messageAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private String roomId;
    private boolean isAdmin;
    private SimpleDateFormat timeFormat;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        roomId = getIntent().getStringExtra("roomId");
        isAdmin = getIntent().getBooleanExtra("isAdmin", false);
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        initViews();
        setupAdapter();
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        tvTitle = findViewById(R.id.tv_title);

        tvTitle.setText(isAdmin ? "公共聊天室" : "聊天室 " + roomId);

        btnSend.setOnClickListener(v -> sendMessage());
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupAdapter() {
        messageAdapter = new MessageAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(messageAdapter);
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;

        ChatMessage message = new ChatMessage(roomId, "admin", "管理员", content, isAdmin ? 1 : 0);
        messages.add(message);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
        etMessage.setText("");
    }

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

        private static final int TYPE_MY_MESSAGE = 0;
        private static final int TYPE_OTHER_MESSAGE = 1;
        private static final int TYPE_SYSTEM = 2;

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == TYPE_MY_MESSAGE ? 
                R.layout.item_message_sent : 
                (viewType == TYPE_SYSTEM ? R.layout.item_message_system : R.layout.item_message_received);
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            ChatMessage message = messages.get(position);
            
            if (getItemViewType(position) == TYPE_SYSTEM) {
                holder.tvContent.setText(message.getContent());
                holder.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));
            } else {
                holder.tvContent.setText(message.isRecalled() ? "消息已撤回" : message.getContent());
                holder.tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));
                
                if (getItemViewType(position) == TYPE_OTHER_MESSAGE) {
                    holder.tvName.setText(message.getSenderName());
                }
                
                holder.itemView.setOnLongClickListener(v -> {
                    if (!message.isRecalled() && message.getSenderToken().equals("admin")) {
                        showRecallDialog(message, position);
                    } else if (!message.isRecalled()) {
                        showCopyDialog(message);
                    }
                    return true;
                });
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage message = messages.get(position);
            if (message.getType() == 1 || message.getType() == 2 || message.getType() == 3) {
                return TYPE_SYSTEM;
            }
            return message.getSenderToken().equals("admin") ? TYPE_MY_MESSAGE : TYPE_OTHER_MESSAGE;
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView tvContent;
            TextView tvTime;
            TextView tvName;

            MessageViewHolder(View itemView) {
                super(itemView);
                tvContent = itemView.findViewById(R.id.tv_content);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvName = itemView.findViewById(R.id.tv_name);
            }
        }
    }

    private void showRecallDialog(ChatMessage message, int position) {
        String[] options = {"复制", "撤回"};
        new MaterialAlertDialogBuilder(this)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        copyToClipboard(message.getContent());
                        break;
                    case 1:
                        message.setRecalled(true);
                        messageAdapter.notifyItemChanged(position);
                        Toast.makeText(this, "消息已撤回", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .show();
    }

    private void showCopyDialog(ChatMessage message) {
        new MaterialAlertDialogBuilder(this)
            .setItems(new String[]{"复制"}, (dialog, which) -> {
                copyToClipboard(message.getContent());
            })
            .show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }
}

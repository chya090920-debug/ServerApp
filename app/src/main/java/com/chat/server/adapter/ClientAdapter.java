package com.chat.server.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.server.R;
import com.chat.server.model.ClientInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClientAdapter extends RecyclerView.Adapter<ClientAdapter.ClientViewHolder> {

    private List<ClientInfo> clients = new ArrayList<>();
    private final OnClientClickListener listener;

    public interface OnClientClickListener {
        void onClientClick(ClientInfo client);
    }

    public ClientAdapter(OnClientClickListener listener) {
        this.listener = listener;
    }

    public void setClients(List<ClientInfo> clients) {
        this.clients = clients != null ? clients : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_client, parent, false);
        return new ClientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClientViewHolder holder, int position) {
        ClientInfo client = clients.get(position);
        holder.tvName.setText(client.getDisplayName());
        holder.tvToken.setText("Token: " + client.getToken().substring(0, 12) + "...");
        
        long onlineTime = System.currentTimeMillis() - client.getConnectTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(onlineTime);
        holder.tvOnlineTime.setText("在线: " + minutes + "分钟");
        
        StringBuilder status = new StringBuilder();
        if (client.isMuted()) status.append("禁言 ");
        if (client.isBanned()) status.append("封禁");
        holder.tvStatus.setText(status.toString());
        
        holder.itemView.setOnClickListener(v -> listener.onClientClick(client));
    }

    @Override
    public int getItemCount() {
        return clients.size();
    }

    static class ClientViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvToken;
        TextView tvOnlineTime;
        TextView tvStatus;

        ClientViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvToken = itemView.findViewById(R.id.tv_token);
            tvOnlineTime = itemView.findViewById(R.id.tv_online_time);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}

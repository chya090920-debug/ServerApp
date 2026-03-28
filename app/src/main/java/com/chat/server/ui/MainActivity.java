package com.chat.server.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.server.R;
import com.chat.server.adapter.ClientAdapter;
import com.chat.server.model.ClientInfo;
import com.chat.server.service.WebSocketServerService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements WebSocketServerService.ServerStatusListener {

    private TextView tvServerStatus;
    private TextView tvOnlineCount;
    private TextView tvRoomCount;
    private TextView tvUptime;
    private RecyclerView rvClients;
    private FloatingActionButton fabAnnouncement;
    
    private WebSocketServerService serverService;
    private boolean isBound = false;
    private ClientAdapter clientAdapter;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketServerService.ServerBinder binder = (WebSocketServerService.ServerBinder) service;
            serverService = binder.getService();
            serverService.setStatusListener(MainActivity.this);
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            serverService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        startAndBindService();
    }

    private void initViews() {
        tvServerStatus = findViewById(R.id.tv_server_status);
        tvOnlineCount = findViewById(R.id.tv_online_count);
        tvRoomCount = findViewById(R.id.tv_room_count);
        tvUptime = findViewById(R.id.tv_uptime);
        rvClients = findViewById(R.id.rv_clients);
        fabAnnouncement = findViewById(R.id.fab_announcement);

        clientAdapter = new ClientAdapter(this::showClientOptions);
        rvClients.setLayoutManager(new LinearLayoutManager(this));
        rvClients.setAdapter(clientAdapter);

        fabAnnouncement.setOnClickListener(v -> showAnnouncementDialog());
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, WebSocketServerService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onClientCountChanged(int count) {
        runOnUiThread(() -> {
            tvOnlineCount.setText(String.valueOf(count));
            updateClientList();
        });
    }

    @Override
    public void onRoomCountChanged(int count) {
        runOnUiThread(() -> tvRoomCount.setText(String.valueOf(count)));
    }

    @Override
    public void onServerStatusChanged(boolean running) {
        runOnUiThread(() -> {
            tvServerStatus.setText(running ? "运行中" : "已停止");
            tvServerStatus.setTextColor(running ? 
                getColor(android.R.color.holo_green_dark) : 
                getColor(android.R.color.holo_red_dark));
        });
    }

    private void updateUI() {
        if (serverService != null) {
            onServerStatusChanged(serverService.isServerRunning());
            onClientCountChanged(serverService.getOnlineCount());
            onRoomCountChanged(serverService.getRoomCount());
            updateUptime();
            updateClientList();
        }
    }

    private void updateUptime() {
        if (serverService != null) {
            long uptime = System.currentTimeMillis() - serverService.getServerStartTime();
            long hours = TimeUnit.MILLISECONDS.toHours(uptime);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
            tvUptime.setText(String.format("%d时%d分", hours, minutes));
        }
    }

    private void updateClientList() {
        if (serverService != null) {
            List<ClientInfo> clients = serverService.getOnlineClients();
            clientAdapter.setClients(clients);
        }
    }

    private void showAnnouncementDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_announcement, null);
        android.widget.EditText etContent = dialogView.findViewById(R.id.et_announcement);

        new MaterialAlertDialogBuilder(this)
            .setTitle("发送系统公告")
            .setView(dialogView)
            .setPositiveButton("发送", (dialog, which) -> {
                String content = etContent.getText().toString().trim();
                if (!content.isEmpty() && serverService != null) {
                    serverService.sendSystemAnnouncement(content);
                    Toast.makeText(this, "公告已发送", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showClientOptions(ClientInfo client) {
        String[] options = {"禁言", "解禁", "封禁"};
        new MaterialAlertDialogBuilder(this)
            .setTitle(client.getDisplayName())
            .setItems(options, (dialog, which) -> {
                if (serverService == null) return;
                switch (which) {
                    case 0:
                        serverService.muteClient(client.getToken());
                        Toast.makeText(this, "已禁言", Toast.LENGTH_SHORT).show();
                        break;
                    case 1:
                        serverService.unmuteClient(client.getToken());
                        Toast.makeText(this, "已解禁", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        new MaterialAlertDialogBuilder(this)
                            .setTitle("确认封禁")
                            .setMessage("封禁后该用户将无法再次连接")
                            .setPositiveButton("确认", (d, w) -> {
                                serverService.banClient(client.getToken());
                                Toast.makeText(this, "已封禁", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                        break;
                }
            })
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_public_chat) {
            Intent intent = new Intent(this, ChatRoomActivity.class);
            intent.putExtra("roomId", WebSocketServerService.PUBLIC_ROOM_ID);
            intent.putExtra("isAdmin", true);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
        }
    }
}

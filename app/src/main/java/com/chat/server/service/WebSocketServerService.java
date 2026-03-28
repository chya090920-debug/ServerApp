package com.chat.server.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.chat.server.R;
import com.chat.server.model.ChatMessage;
import com.chat.server.model.ChatRoom;
import com.chat.server.model.ClientInfo;
import com.chat.server.ui.MainActivity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketServerService extends Service {
    private static final String TAG = "WebSocketServerService";
    private static final String CHANNEL_ID = "chat_server_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PORT = 45733;

    private ChatWebSocketServer server;
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final Set<String> bannedTokens = new HashSet<>();
    private final Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private long serverStartTime;
    private ServerStatusListener statusListener;
    
    // 公共聊天室ID
    public static final String PUBLIC_ROOM_ID = "000000000000";

    public interface ServerStatusListener {
        void onClientCountChanged(int count);
        void onRoomCountChanged(int count);
        void onServerStatusChanged(boolean running);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serverStartTime = System.currentTimeMillis();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        initPublicRoom();
        startServer();
    }

    private void initPublicRoom() {
        ChatRoom publicRoom = new ChatRoom(PUBLIC_ROOM_ID, "admin");
        rooms.put(PUBLIC_ROOM_ID, publicRoom);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ServerBinder();
    }

    public class ServerBinder extends android.os.Binder {
        public WebSocketServerService getService() {
            return WebSocketServerService.this;
        }
    }

    public void setStatusListener(ServerStatusListener listener) {
        this.statusListener = listener;
        if (listener != null) {
            listener.onClientCountChanged(clients.size());
            listener.onRoomCountChanged(rooms.size());
            listener.onServerStatusChanged(server != null && server.isRunning());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "聊天服务器",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("聊天服务器运行中");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("聊天服务器运行中")
            .setContentText("端口: " + PORT + " | 在线: " + clients.size() + " | 房间: " + rooms.size())
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private void startServer() {
        try {
            server = new ChatWebSocketServer(PORT);
            server.setReuseAddr(true);
            server.start();
            Log.d(TAG, "WebSocket Server started on port " + PORT);
            if (statusListener != null) {
                statusListener.onServerStatusChanged(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
        }
    }

    private class ChatWebSocketServer extends WebSocketServer {
        private volatile boolean running = false;

        public ChatWebSocketServer(int port) {
            super(new InetSocketAddress(port));
        }

        /**
         * 检查服务器是否正在运行
         * Java-WebSocket库的WebSocketServer没有内置isRunning()方法，
         * 因此我们需要自己维护运行状态
         */
        public boolean isRunning() {
            return running && getAddress() != null;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            Log.d(TAG, "New connection: " + conn.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            ClientInfo client = findClientByConnection(conn);
            if (client != null) {
                // 从所有房间移除
                for (String roomId : client.getJoinedRooms()) {
                    ChatRoom room = rooms.get(roomId);
                    if (room != null) {
                        room.removeMember(client.getToken());
                        broadcastLeaveMessage(room, client);
                    }
                }
                clients.remove(client.getToken());
                notifyClientCountChanged();
            }
            Log.d(TAG, "Connection closed: " + code + " - " + reason);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String action = json.get("action").getAsString();
                
                ClientInfo client = findClientByConnection(conn);
                
                switch (action) {
                    case "auth":
                        handleAuth(conn, json);
                        break;
                    case "join_room":
                        if (client != null) handleJoinRoom(client, json);
                        break;
                    case "leave_room":
                        if (client != null) handleLeaveRoom(client, json);
                        break;
                    case "send_message":
                        if (client != null) handleSendMessage(client, json);
                        break;
                    case "create_room":
                        if (client != null) handleCreateRoom(client, json);
                        break;
                    case "typing":
                        if (client != null) handleTyping(client, json);
                        break;
                    case "recall_message":
                        if (client != null) handleRecallMessage(client, json);
                        break;
                    case "heartbeat":
                        // 心跳响应
                        sendResponse(conn, "heartbeat_ack", null);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing message", e);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            Log.e(TAG, "WebSocket error", ex);
        }

        @Override
        public void onStart() {
            running = true;
            Log.d(TAG, "Server started successfully");
        }

        @Override
        public void onStop() {
            running = false;
            Log.d(TAG, "Server stopped");
        }
    }

    private ClientInfo findClientByConnection(WebSocket conn) {
        for (ClientInfo client : clients.values()) {
            if (client.getConnection() == conn) {
                return client;
            }
        }
        return null;
    }

    private void handleAuth(WebSocket conn, JsonObject json) {
        String token = json.get("token").getAsString();
        
        // 检查是否被封禁
        if (bannedTokens.contains(token)) {
            sendResponse(conn, "auth_failed", createErrorData("您已被封禁"));
            conn.close();
            return;
        }

        ClientInfo client = new ClientInfo(token, conn);
        
        // 检查是否是重连
        ClientInfo oldClient = clients.get(token);
        if (oldClient != null) {
            // 继承之前的状态
            client.setDisplayName(oldClient.getDisplayName());
            client.setMuted(oldClient.isMuted());
        }
        
        clients.put(token, client);
        notifyClientCountChanged();

        JsonObject data = new JsonObject();
        data.addProperty("success", true);
        data.addProperty("displayName", client.getDisplayName());
        sendResponse(conn, "auth_success", data);
    }

    private void handleJoinRoom(ClientInfo client, JsonObject json) {
        String roomId = json.get("roomId").getAsString();
        String password = json.has("password") ? json.get("password").getAsString() : "";
        
        ChatRoom room = rooms.get(roomId);
        if (room == null) {
            sendResponse(client.getConnection(), "join_failed", createErrorData("房间不存在"));
            return;
        }

        if (room.hasPassword() && !room.verifyPassword(password)) {
            sendResponse(client.getConnection(), "join_failed", createErrorData("密码错误"));
            return;
        }

        room.addMember(client.getToken());
        client.getJoinedRooms().add(roomId);

        // 发送加入成功响应
        JsonObject data = new JsonObject();
        data.addProperty("roomId", roomId);
        data.addProperty("success", true);
        sendResponse(client.getConnection(), "join_success", data);

        // 发送历史消息
        JsonObject historyData = new JsonObject();
        historyData.addProperty("roomId", roomId);
        historyData.add("messages", gson.toJsonTree(room.getMessages()));
        sendResponse(client.getConnection(), "room_history", historyData);

        // 广播加入消息
        broadcastJoinMessage(room, client);
        notifyRoomCountChanged();
    }

    private void handleLeaveRoom(ClientInfo client, JsonObject json) {
        String roomId = json.get("roomId").getAsString();
        ChatRoom room = rooms.get(roomId);
        if (room != null) {
            room.removeMember(client.getToken());
            client.getJoinedRooms().remove(roomId);
            broadcastLeaveMessage(room, client);
        }
        sendResponse(client.getConnection(), "leave_success", createSimpleData("roomId", roomId));
    }

    private void handleSendMessage(ClientInfo client, JsonObject json) {
        if (client.isMuted()) {
            sendResponse(client.getConnection(), "send_failed", createErrorData("您已被禁言"));
            return;
        }

        String roomId = json.get("roomId").getAsString();
        String content = json.get("content").getAsString();
        int type = json.has("type") ? json.get("type").getAsInt() : 0;

        ChatRoom room = rooms.get(roomId);
        if (room == null) return;

        ChatMessage message = new ChatMessage(roomId, client.getToken(), client.getDisplayName(), content, type);
        room.addMessage(message);

        // 广播消息给房间内所有人
        broadcastMessage(room, message);
    }

    private void handleCreateRoom(ClientInfo client, JsonObject json) {
        String password = json.has("password") ? json.get("password").getAsString() : "";
        String roomId = generateRoomId();

        ChatRoom room = new ChatRoom(roomId, client.getToken());
        if (!password.isEmpty()) {
            room.setPassword(password);
        }
        rooms.put(roomId, room);

        JsonObject data = new JsonObject();
        data.addProperty("roomId", roomId);
        data.addProperty("success", true);
        sendResponse(client.getConnection(), "room_created", data);
        notifyRoomCountChanged();
    }

    private void handleTyping(ClientInfo client, JsonObject json) {
        String roomId = json.get("roomId").getAsString();
        boolean isTyping = json.get("isTyping").getAsBoolean();
        
        ChatRoom room = rooms.get(roomId);
        if (room == null) return;

        JsonObject data = new JsonObject();
        data.addProperty("roomId", roomId);
        data.addProperty("userToken", client.getToken());
        data.addProperty("userName", client.getDisplayName());
        data.addProperty("isTyping", isTyping);

        broadcastToRoom(room, "user_typing", data, client.getToken());
    }

    private void handleRecallMessage(ClientInfo client, JsonObject json) {
        String roomId = json.get("roomId").getAsString();
        String messageId = json.get("messageId").getAsString();

        ChatRoom room = rooms.get(roomId);
        if (room == null) return;

        for (ChatMessage msg : room.getMessages()) {
            if (msg.getId().equals(messageId) && msg.getSenderToken().equals(client.getToken())) {
                msg.setRecalled(true);
                JsonObject data = new JsonObject();
                data.addProperty("roomId", roomId);
                data.addProperty("messageId", messageId);
                broadcastToRoom(room, "message_recalled", data, null);
                break;
            }
        }
    }

    private String generateRoomId() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        // 确保不重复
        String id = sb.toString();
        if (rooms.containsKey(id)) {
            return generateRoomId();
        }
        return id;
    }

    private void broadcastMessage(ChatRoom room, ChatMessage message) {
        JsonObject data = new JsonObject();
        data.add("message", gson.toJsonTree(message));
        broadcastToRoom(room, "new_message", data, null);
    }

    private void broadcastJoinMessage(ChatRoom room, ClientInfo client) {
        ChatMessage joinMsg = new ChatMessage(room.getRoomId(), client.getToken(), 
            client.getDisplayName(), client.getDisplayName() + " 加入了房间", 2);
        room.addMessage(joinMsg);
        broadcastMessage(room, joinMsg);
    }

    private void broadcastLeaveMessage(ChatRoom room, ClientInfo client) {
        ChatMessage leaveMsg = new ChatMessage(room.getRoomId(), client.getToken(), 
            client.getDisplayName(), client.getDisplayName() + " 离开了房间", 3);
        room.addMessage(leaveMsg);
        broadcastMessage(room, leaveMsg);
    }

    private void broadcastToRoom(ChatRoom room, String action, JsonObject data, String excludeToken) {
        JsonObject response = new JsonObject();
        response.addProperty("action", action);
        response.add("data", data);
        String message = gson.toJson(response);

        for (String token : room.getMembers()) {
            if (excludeToken != null && token.equals(excludeToken)) continue;
            ClientInfo client = clients.get(token);
            if (client != null && client.getConnection() != null && client.getConnection().isOpen()) {
                client.getConnection().send(message);
            }
        }
    }

    private void sendResponse(WebSocket conn, String action, JsonObject data) {
        JsonObject response = new JsonObject();
        response.addProperty("action", action);
        if (data != null) {
            response.add("data", data);
        }
        conn.send(gson.toJson(response));
    }

    private JsonObject createErrorData(String error) {
        JsonObject data = new JsonObject();
        data.addProperty("error", error);
        return data;
    }

    private JsonObject createSimpleData(String key, String value) {
        JsonObject data = new JsonObject();
        data.addProperty(key, value);
        return data;
    }

    private void notifyClientCountChanged() {
        handler.post(() -> {
            updateNotification();
            if (statusListener != null) {
                statusListener.onClientCountChanged(clients.size());
            }
        });
    }

    private void notifyRoomCountChanged() {
        handler.post(() -> {
            updateNotification();
            if (statusListener != null) {
                statusListener.onRoomCountChanged(rooms.size());
            }
        });
    }

    // 管理功能
    public void banClient(String token) {
        bannedTokens.add(token);
        ClientInfo client = clients.get(token);
        if (client != null && client.getConnection() != null) {
            sendResponse(client.getConnection(), "banned", createErrorData("您已被封禁"));
            client.getConnection().close();
        }
    }

    public void muteClient(String token) {
        ClientInfo client = clients.get(token);
        if (client != null) {
            client.setMuted(true);
            sendResponse(client.getConnection(), "muted", createErrorData("您已被禁言"));
        }
    }

    public void unmuteClient(String token) {
        ClientInfo client = clients.get(token);
        if (client != null) {
            client.setMuted(false);
            sendResponse(client.getConnection(), "unmuted", null);
        }
    }

    public void sendSystemAnnouncement(String content) {
        ChatRoom publicRoom = rooms.get(PUBLIC_ROOM_ID);
        if (publicRoom != null) {
            ChatMessage message = new ChatMessage(PUBLIC_ROOM_ID, "admin", "系统公告", content, 1);
            publicRoom.addMessage(message);
            broadcastMessage(publicRoom, message);
        }
    }

    // 获取状态信息
    public int getOnlineCount() {
        return clients.size();
    }

    public int getRoomCount() {
        return rooms.size();
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public List<ClientInfo> getOnlineClients() {
        return new ArrayList<>(clients.values());
    }

    public boolean isServerRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping server", e);
            }
        }
    }
}

package com.example.hw_online_paint;


import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity2 extends AppCompatActivity {

    private DrawingView drawingView;
    private TextView tvNotice;

    private Button btnUndo;
    private Button btnErase;
    private Button btnClear;

    private volatile Socket socket;
    private volatile PrintWriter out;

    private volatile boolean connected = false;
    private volatile boolean closing = false;

    // 單一背景執行緒，依序傳送繪畫訊息
    private final ExecutorService sender =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        // 1. 連接 XML 元件
        FrameLayout canvasContainer =
                findViewById(R.id.canvasContainer);

        tvNotice = findViewById(R.id.tvNotice);
        btnUndo = findViewById(R.id.btnUndo);
        btnErase = findViewById(R.id.btnErase);
        btnClear = findViewById(R.id.btnClear);

        Button btnLeave =
                findViewById(R.id.btnLeave);

        // 2. 建立畫布
        drawingView = new DrawingView(this);

        canvasContainer.addView(
                drawingView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        // 3. 取得第一頁的登入資料
        String name =
                getIntent().getStringExtra("name");

        String ip =
                getIntent().getStringExtra("ip");

        String portText =
                getIntent().getStringExtra("port");

        if (name == null || ip == null ||
                portText == null) {
            finish();
            return;
        }

        int port;

        try {
            port = Integer.parseInt(portText);

            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            Toast.makeText(this,
                    "Port 格式錯誤",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 防止名稱破壞通訊格式
        String safeName = name.trim()
                .replace('|', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ');

        if (safeName.isEmpty()) {
            finish();
            return;
        }

        // 4. 連線前暫時停用繪畫按鈕
        setToolsEnabled(false);

        tvNotice.setText("正在連線至 " +
                ip + ":" + port);

        // 5. 將畫筆事件傳給 Server
        drawingView.setOnStrokeListener(
                new DrawingView.OnStrokeListener() {

                    @Override
                    public void onBegin(
                            String id, float x, float y) {

                        send("BEGIN|" + id +
                                "|" + x + "|" + y);
                    }

                    @Override
                    public void onPoint(
                            String id, float x, float y) {

                        send("POINT|" + id +
                                "|" + x + "|" + y);
                    }

                    @Override
                    public void onEnd(String id) {
                        send("END|" + id);
                    }

                    @Override
                    public void onRemove(String id) {
                        send("REMOVE|" + id);
                    }
                }
        );

        // 6. UNDO：撤銷自己的上一筆
        btnUndo.setOnClickListener(v -> {

            String id = drawingView.undoMine();

            if (id != null) {
                send("REMOVE|" + id);
            }
        });

        // 7. ERASE：切換橡皮擦
        btnErase.setOnClickListener(v -> {

            boolean erasing =
                    !drawingView.isEraserMode();

            drawingView.setEraserMode(erasing);

            btnErase.setText(
                    erasing ? "DRAW" : "ERASE"
            );

            tvNotice.setText(
                    erasing ? "橡皮擦模式" : "畫筆模式"
            );
        });

        // 8. CLEAR：清除自己的所有筆跡
        btnClear.setOnClickListener(v -> {
            drawingView.removeOwner(myId);
            send("CLEAR");
        });

        // 9. LEAVE：通知 Server 並離開
        btnLeave.setOnClickListener(v -> leaveChat());

        // 10. 真正建立 Socket 連線
        connectToServer(safeName, ip, port);
    }

    private String myId = "";

    private void setToolsEnabled(boolean enabled) {
        btnUndo.setEnabled(enabled);
        btnErase.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    // 建立 Socket，持續接收 Server 訊息
    private void connectToServer(
            String name, String ip, int port) {

        new Thread(() -> {

            Socket s = new Socket();
            socket = s;

            try {
                // 連線最多等待 5 秒
                s.connect(
                        new InetSocketAddress(ip, port),
                        5000
                );

                if (closing) return;

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                s.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                out = new PrintWriter(
                        new OutputStreamWriter(
                                s.getOutputStream(),
                                StandardCharsets.UTF_8
                        ), true
                );

                // 告訴 Server 自己要加入畫室
                out.println("JOIN|" + name);

                if (out.checkError()) {
                    throw new IOException("JOIN 傳送失敗");
                }

                // 持續接收 Server 廣播
                String line;

                while ((line = in.readLine()) != null) {

                    String message = line;

                    runOnUiThread(() ->
                            handleServerMessage(message)
                    );
                }

                runOnUiThread(() -> {
                    if (!closing) {
                        disconnectAndReturn(
                                "Server 已斷線"
                        );
                    }
                });

            } catch (IOException e) {

                runOnUiThread(() -> {
                    if (!closing) {
                        disconnectAndReturn(
                                "連線失敗：" + e.getMessage()
                        );
                    }
                });

            } finally {
                try {
                    s.close();
                } catch (IOException ignored) {
                }

                connected = false;
            }

        }).start();
    }

    // 背景依序傳送訊息
    private void send(String line) {

        if (!connected || closing) return;

        sender.execute(() -> {

            if (closing) return;

            PrintWriter writer = out;

            if (writer != null) {
                writer.println(line);
            }
        });
    }

    // 解析 Server 傳來的資料
    private void handleServerMessage(String line) {

        if (closing) return;

        try {
            String[] p = line.split("\\|", 6);

            switch (p[0]) {

                // Server 分配 ID 與畫筆顏色
                case "WELCOME":

                    if (p.length < 3) return;

                    myId = p[1];

                    int myColor =
                            Integer.parseInt(p[2]);

                    connected = true;

                    drawingView.setIdentity(
                            myId, myColor
                    );

                    setToolsEnabled(true);

                    tvNotice.setText(
                            "已連線｜可以開始繪畫"
                    );
                    break;

                // Server 廣播新筆跡
                case "BEGIN":

                    if (p.length < 6) return;

                    drawingView.remoteBegin(
                            p[1],
                            p[2],
                            Integer.parseInt(p[3]),
                            Float.parseFloat(p[4]),
                            Float.parseFloat(p[5])
                    );
                    break;

                // Server 廣播畫筆移動
                case "POINT":

                    if (p.length < 5) return;

                    drawingView.remotePoint(
                            p[1],
                            p[2],
                            Float.parseFloat(p[3]),
                            Float.parseFloat(p[4])
                    );
                    break;

                // 一筆完成
                case "END":
                    break;

                // 移除一筆
                case "REMOVE":

                    if (p.length < 3) return;

                    drawingView.removeStroke(
                            p[1], p[2]
                    );
                    break;

                // 清除特定使用者
                case "CLEAR_USER":

                    if (p.length < 2) return;

                    drawingView.removeOwner(p[1]);
                    break;

                // 清空所有人筆跡
                case "CLEAR_ALL":
                    drawingView.clearAll();
                    break;

                // 使用者離開
                case "USER_LEFT":
                    // 離線者的筆跡已由 CLEAR_USER 清除
                    break;

                // 系統通知
                case "NOTICE":

                    if (p.length >= 2) {
                        tvNotice.setText(p[1]);
                    }
                    break;

                // 被 Server 踢出
                case "KICKED":

                    disconnectAndReturn(
                            p.length >= 2
                                    ? p[1]
                                    : "你已被踢出畫室"
                    );
                    break;

                // Server 關閉
                case "SERVER_CLOSED":

                    disconnectAndReturn(
                            "Server 已關閉"
                    );
                    break;

                // 使用者清單由 Server 管理
                case "USER":
                    break;
            }

        } catch (NumberFormatException e) {
            tvNotice.setText("收到無效的座標資料");
        }
    }

    // 正常離開
    private void leaveChat() {

        if (closing) return;

        closing = true;
        connected = false;

        setToolsEnabled(false);

        sender.execute(() -> {

            PrintWriter writer = out;

            if (writer != null) {
                writer.println("LEAVE");
            }

            closeSocket();

            runOnUiThread(this::finish);
        });
    }

    // 被踢出或 Server 斷線
    private void disconnectAndReturn(String reason) {

        if (closing) return;

        closing = true;
        connected = false;

        Toast.makeText(
                this, reason, Toast.LENGTH_LONG
        ).show();

        closeSocket();
        finish();
    }

    private void closeSocket() {

        Socket s = socket;
        socket = null;

        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }

        out = null;
    }

    @Override
    protected void onDestroy() {

        closing = true;

        closeSocket();
        sender.shutdownNow();

        super.onDestroy();
    }
}

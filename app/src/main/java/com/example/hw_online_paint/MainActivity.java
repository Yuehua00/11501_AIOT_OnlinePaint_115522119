package com.example.hw_online_paint;


import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText editName =
                findViewById(R.id.editName);

        EditText editIp =
                findViewById(R.id.editIp);

        EditText editPort =
                findViewById(R.id.editPort);

        Button btnConnect =
                findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(view -> {

            String name = editName.getText()
                    .toString().trim();

            String ip = editIp.getText()
                    .toString().trim();

            String port = editPort.getText()
                    .toString().trim();

            if (name.isEmpty() ||
                    ip.isEmpty() ||
                    port.isEmpty()) {

                Toast.makeText(this,
                        "請輸入所有資料",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(
                    MainActivity.this,
                    MainActivity2.class
            );

            intent.putExtra("name", name);
            intent.putExtra("ip", ip);
            intent.putExtra("port", port);

            startActivity(intent);
        });
    }
}

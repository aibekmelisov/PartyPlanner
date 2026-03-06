package com.example.partyplanner.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.partyplanner.R;
import com.example.partyplanner.data.PartyRepository;
import com.google.android.material.button.MaterialButton;

public class RsvpActivity extends AppCompatActivity {

    private PartyRepository repo;
    private String eventId;
    private String personId;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rsvp);

        repo = new PartyRepository(getApplicationContext(), /* baseUrl */ ""); // если baseUrl нужен только для retrofit, можно сделать второй конструктор без него

        Uri data = getIntent().getData();
        if (data == null) {
            Toast.makeText(this, "Нет данных приглашения", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        eventId = data.getQueryParameter("eventId");
        personId = data.getQueryParameter("personId");
        token = data.getQueryParameter("token"); // опционально

        if (eventId == null || personId == null) {
            Toast.makeText(this, "Ссылка повреждена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialButton btnGoing = findViewById(R.id.btnGoing);
        MaterialButton btnMaybe = findViewById(R.id.btnMaybe);
        MaterialButton btnDeclined = findViewById(R.id.btnDeclined);

        btnGoing.setOnClickListener(v -> set("GOING"));
        btnMaybe.setOnClickListener(v -> set("MAYBE"));
        btnDeclined.setOnClickListener(v -> set("DECLINED"));
    }

    private void set(String status) {
        // минимум: просто обновляем статус (у тебя это пишет в Firestore)
        repo.updateGuestStatus(eventId, personId, status);
        Toast.makeText(this, "Ответ отправлен: " + status, Toast.LENGTH_SHORT).show();
        finish();
    }
}
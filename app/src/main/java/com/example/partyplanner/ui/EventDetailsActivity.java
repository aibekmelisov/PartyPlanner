package com.example.partyplanner.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.partyplanner.R;
import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.PersonEntity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class EventDetailsActivity extends AppCompatActivity {

    private EventWithDetails current;

    private EventDetailsViewModel vm;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        String id = getIntent().getStringExtra("eventId");

        // Если в layout теперь ImageButton — тут тоже ImageButton.
        ImageButton btnGuests = findViewById(R.id.btnGuests);
        ImageButton btnEdit   = findViewById(R.id.btnEdit);
        ImageButton btnInvite = findViewById(R.id.btnInvite);

        // Если Delete остался обычной кнопкой - оставляем Button.
        ImageButton btnDelete = findViewById(R.id.btnDelete);


        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvDate = findViewById(R.id.tvDate);
        TextView tvAddress = findViewById(R.id.tvAddress);
        TextView tvOrganizer = findViewById(R.id.tvOrganizer);
        TextView tvGuests = findViewById(R.id.tvGuests);

        TextView tvTemplate = findViewById(R.id.tvTemplate);

        vm = new ViewModelProvider(this).get(EventDetailsViewModel.class);

        vm.getEvent(id).observe(this, e -> {
            if (e == null) return;
            current = e;

            tvTitle.setText(e.event.title);

            String date = DateFormat.getDateTimeInstance().format(new Date(e.event.dateTime));
            tvDate.setText("Date: " + date);

            tvAddress.setText("Address: " + e.event.address);

            tvOrganizer.setText("Organizer: " + (e.organizer != null ? e.organizer.name : "—"));

            tvGuests.setText("Guests: " + (e.guests != null ? e.guests.size() : 0));
            String tid = (e.event.inviteTemplateId == null || e.event.inviteTemplateId.trim().isEmpty())
                    ? "classic"
                    : e.event.inviteTemplateId.trim();

            tvTemplate.setText("Template: " + tid);
        });


        btnGuests.setOnClickListener(v -> {
            Intent i = new Intent(this, GuestsActivity.class);
            i.putExtra("eventId", id);
            startActivity(i);
        });

        btnEdit.setOnClickListener(v -> {
            Intent i = new Intent(this, EditEventActivity.class);
            i.putExtra("eventId", id);
            startActivity(i);
        });

        btnInvite.setOnClickListener(v -> showInviteChooser());
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить событие?")
                    .setMessage("Это действие нельзя отменить.")
                    .setPositiveButton("Удалить", (d, which) -> {
                        vm.delete(id);
                        finish();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
    }

    private void sendInviteEmail() {
        if (current == null || current.guests == null || current.guests.isEmpty()) return;

        // соберём гостей, у которых contacts похож на email
        ArrayList<PersonEntity> emailGuests = new ArrayList<>();
        for (PersonEntity p : current.guests) {
            if (p == null || p.contacts == null) continue;
            String c = p.contacts.trim();
            if (c.contains("@")) emailGuests.add(p);
        }

        if (emailGuests.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Нет email")
                    .setMessage("У гостей не заполнены email в поле contacts.")
                    .setPositiveButton("Ок", null)
                    .show();
            return;
        }

        // выбираем одного получателя
        String[] names = new String[emailGuests.size()];
        for (int i = 0; i < emailGuests.size(); i++) {
            PersonEntity p = emailGuests.get(i);
            names[i] = p.name + " (" + p.contacts.trim() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Кому отправить email?")
                .setItems(names, (d, which) -> {
                    PersonEntity p = emailGuests.get(which);

                    String subject = "Invitation: " + current.event.title;

                    // 1) создаём invite в Firestore
                    if (vm == null) return;

                    vm.createInvite(current, p.id, inviteId -> {                        if (inviteId == null) {
                            runOnUiThread(() -> new AlertDialog.Builder(this)
                                    .setTitle("Ошибка")
                                    .setMessage("Не удалось создать invite в Firestore.")
                                    .setPositiveButton("Ок", null)
                                    .show());
                            return;
                        }

                        // 2) собираем тело письма уже с web-ссылкой
                        String body = com.example.partyplanner.ui.invite.InviteComposer
                                .buildTextForRecipientWithInviteId(current, inviteId);

                        // 3) открываем email клиент
                        runOnUiThread(() -> {
                            Intent intent = new Intent(Intent.ACTION_SENDTO);
                            intent.setData(Uri.parse("mailto:"));
                            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{p.contacts.trim()});
                            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                            intent.putExtra(Intent.EXTRA_TEXT, body);

                            startActivity(Intent.createChooser(intent, "Send invitation"));
                        });
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showInviteChooser() {
        if (current == null) return;

        String[] items = new String[] {
                "Поделиться текстом",
                "Поделиться открыткой (PNG)",
                "Отправить на email (как было)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Приглашение")
                .setItems(items, (d, which) -> {
                    if (which == 0) shareInviteText();
                    else if (which == 1) shareInviteImage();
                    else sendInviteEmail();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void shareInviteText() {
        String text = com.example.partyplanner.ui.invite.InviteComposer.buildText(current);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT,
                com.example.partyplanner.ui.invite.InviteComposer.buildSubject(current));
        intent.putExtra(Intent.EXTRA_TEXT, text);

        startActivity(Intent.createChooser(intent, "Поделиться приглашением"));
    }

    private void shareInviteImage() {
        try {
            android.graphics.Bitmap bmp =
                    com.example.partyplanner.ui.invite.InviteCardRenderer.renderToBitmap(this, current);

            android.net.Uri uri =
                    com.example.partyplanner.ui.invite.InviteCardRenderer.savePngAndGetUri(
                            this, bmp, "invite_" + current.event.id);

            if (uri == null) return;

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            //добавим текст в подпись
            intent.putExtra(Intent.EXTRA_TEXT,
                    com.example.partyplanner.ui.invite.InviteComposer.buildText(current));

            startActivity(Intent.createChooser(intent, "Поделиться открыткой"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showReminderDialog() {
        if (current == null) return;

        androidx.appcompat.widget.SwitchCompat sw =
                new androidx.appcompat.widget.SwitchCompat(this);
        sw.setText("Включить напоминание");
        sw.setChecked(current.event.reminderEnabled);

        new AlertDialog.Builder(this)
                .setTitle("Напоминание")
                .setView(sw)
                .setPositiveButton("Далее", (d, w) -> {

                    if (!sw.isChecked()) {
                        current.event.reminderEnabled = false;
                        current.event.reminderAtMillis = 0;
                        saveAndReschedule();
                        return;
                    }

                    pickReminderDateTime();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickReminderDateTime() {

        Calendar now = Calendar.getInstance();

        new DatePickerDialog(this, (view, year, month, day) -> {

            Calendar picked = Calendar.getInstance();
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, day);

            new TimePickerDialog(this, (tp, hour, minute) -> {

                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);

                current.event.reminderEnabled = true;
                current.event.reminderAtMillis = picked.getTimeInMillis();

                saveAndReschedule();

            }, now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true).show();

        }, now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveAndReschedule() {

        EventDetailsViewModel vm =
                new ViewModelProvider(this).get(EventDetailsViewModel.class);

        vm.save(current.event);

        com.example.partyplanner.reminders.ReminderScheduler
                .scheduleExact(this, current);
    }

}

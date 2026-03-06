package com.example.partyplanner.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.data.GuestWithStatus;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonEntity;

import java.util.ArrayList;
import java.util.List;

public class GuestsActivity extends AppCompatActivity {

    private String eventId;
    private GuestsViewModel vm;
    private GuestsAdapter adapter;

    private List<PersonEntity> allPersonsCache = new ArrayList<>();
    private List<com.example.partyplanner.data.GroupEntity> groupsCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guests);
        com.google.android.material.appbar.MaterialToolbar tb = findViewById(R.id.toolbarGuests);
        tb.setNavigationOnClickListener(v -> finish());


        eventId = getIntent().getStringExtra("eventId");

        TextView tvHeader = findViewById(R.id.tvHeader);
        com.google.android.material.floatingactionbutton.FloatingActionButton btnAdd =
                findViewById(R.id.btnAddGuest);

        RecyclerView rv = findViewById(R.id.rvGuests);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                       @NonNull android.view.View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                outRect.bottom = 18;
                if (parent.getChildAdapterPosition(view) == 0) outRect.top = 8;
            }
        });

        TextView tvStats = findViewById(R.id.tvStats);


        vm = new ViewModelProvider(this).get(GuestsViewModel.class);

        vm.startRealtime(eventId);

        adapter = new GuestsAdapter(new GuestsAdapter.Listener() {
            @Override public void onClick(com.example.partyplanner.data.GuestWithStatus g) { }

            @Override public void onLongClick(com.example.partyplanner.data.GuestWithStatus g) {
                new AlertDialog.Builder(GuestsActivity.this)
                        .setTitle("Удалить гостя?")
                        .setMessage(g.name)
                        .setPositiveButton("Удалить", (d,w) -> vm.removeGuest(eventId, g.id))
                        .setNegativeButton("Отмена", null)
                        .show();
            }

            @Override
            public void onStatusChange(GuestWithStatus g, String newStatus) {
                vm.updateStatus(eventId, g.id, newStatus);
            }
        });
        rv.setAdapter(adapter);


        // наблюдаем событие и его гостей
        vm.getGuests(eventId).observe(this, guests -> {
            int total = (guests != null) ? guests.size() : 0;
            tvHeader.setText("Guests: " + total);
            adapter.submitGuests(guests);
            vm.getRsvpStats(eventId, s -> {
                tvStats.setText(
                        "Going: " + s.going +
                                " | Maybe: " + s.maybe +
                                " | Declined: " + s.declined +
                                " | Invited: " + s.invited
                );
            });

        });

        // кешируем всех людей для выбора
        vm.getAllPersons().observe(this, list -> {
            allPersonsCache = (list != null) ? list : new ArrayList<>();
        });

        btnAdd.setOnClickListener(v -> showAddGuestDialog());

        vm.getGroups().observe(this, groups -> {
            groupsCache = (groups != null) ? groups : new ArrayList<>();
        });
    }



    private void showAddGuestDialog() {
        if (allPersonsCache == null || allPersonsCache.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Нет людей")
                    .setMessage("Сначала добавь людей в разделе «Люди».")
                    .setPositiveButton("Открыть Люди", (d,w) ->
                            startActivity(new Intent(this, PersonsActivity.class)))
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }

        // 1) Собираем список групп (включая "Все" и "Без группы")
        ArrayList<String> groupTitles = new ArrayList<>();
        groupTitles.add("Все");
        groupTitles.add("Без группы");
        for (com.example.partyplanner.data.GroupEntity g : groupsCache) {
            groupTitles.add(g.name);
        }

        String[] groupsArr = groupTitles.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Выберите группу")
                .setItems(groupsArr, (d, which) -> {
                    String chosen = groupsArr[which];
                    showGroupActionsDialog(chosen);
                })
                .setNeutralButton("+ Новый человек", (d, w) ->
                        startActivity(new Intent(this, EditPersonActivity.class)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showGroupActionsDialog(String groupTitle) {
        // Фильтруем людей по выбранной группе
        List<PersonEntity> filtered = filterPersonsByGroupTitle(groupTitle);

        if (filtered.isEmpty()) {
            Toast.makeText(this, "В этой группе нет людей", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] actions = new String[] {
                "Добавить ВСЮ группу (" + filtered.size() + ")",
                "Выбрать одного человека"
        };

        new AlertDialog.Builder(this)
                .setTitle("Группа: " + groupTitle)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        ArrayList<String> ids = new ArrayList<>();
                        for (PersonEntity p : filtered) ids.add(p.id);
                        vm.addGuestsBulkCheckedWithTimeConflict(eventId, ids, report -> {
                            String msg = "✅ Добавлено: " + report.added;
                            if (report.skippedDuplicates > 0) msg += "\n⚠ Уже были: " + report.skippedDuplicates;
                            if (report.skippedBusy > 0) msg += "\n❌ Конфликт по времени: " + report.skippedBusy;
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        });                        showPickOnePersonDialog(filtered);
                    }
                })
                .setNegativeButton("Назад", (d,w) -> showAddGuestDialog())
                .show();
    }

    private void showPickOnePersonDialog(List<PersonEntity> filtered) {
        String[] names = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) names[i] = filtered.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("Добавить гостя")
                .setItems(names, (d, which) -> {
                    PersonEntity p = filtered.get(which);
                    vm.addGuestCheckedWithTimeConflict(eventId, p.id, code -> {
                        if (code == PartyRepository.ADD_OK) {
                            Toast.makeText(this, "✅ Добавлен", Toast.LENGTH_SHORT).show();
                        } else if (code == PartyRepository.ADD_DUPLICATE_SAME_EVENT) {
                            Toast.makeText(this, "⚠ Уже есть в этом мероприятии", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "❌ Ошибка: у него уже есть мероприятие в это время", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private List<PersonEntity> filterPersonsByGroupTitle(String groupTitle) {
        // Все
        if ("Все".equalsIgnoreCase(groupTitle)) {
            return new ArrayList<>(allPersonsCache);
        }

        // Без группы
        if ("Без группы".equalsIgnoreCase(groupTitle)) {
            ArrayList<PersonEntity> out = new ArrayList<>();
            for (PersonEntity p : allPersonsCache) {
                if (p.groupId == null || p.groupId.trim().isEmpty()) out.add(p);
            }
            return out;
        }

        // Обычная группа: ищем её id по имени
        String gid = null;
        for (com.example.partyplanner.data.GroupEntity g : groupsCache) {
            if (g != null && g.name != null && g.name.equalsIgnoreCase(groupTitle)) {
                gid = g.id;
                break;
            }
        }
        if (gid == null) return new ArrayList<>();

        ArrayList<PersonEntity> out = new ArrayList<>();
        for (PersonEntity p : allPersonsCache) {
            if (p.groupId != null && p.groupId.equals(gid)) out.add(p);
        }
        return out;
    }

}

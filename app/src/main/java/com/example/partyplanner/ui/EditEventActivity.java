package com.example.partyplanner.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.partyplanner.R;
import com.example.partyplanner.data.EventEntity;
import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PersonEntity;
import com.example.partyplanner.data.PersonWithGroup;
import com.example.partyplanner.reminders.ReminderScheduler;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class EditEventActivity extends AppCompatActivity {

    // Main fields
    private EditText etTitle, etAddress, etPosterUrl;
    private TextView tvChosen, tvGuests;
    private Spinner spOrganizer;
    private Button btnGuests;

    // Invitation fields
    private Spinner spInviteTemplate;
    private EditText etInviteMessage;
    private SwitchCompat swInviteMap;
    private String[] inviteTemplateIds;

    // Reminder fields
    private SwitchCompat swReminder;
    private Spinner spReminderType;
    private Button btnPickReminderTime;
    private TextView tvReminderChosen;

    private long manualReminderAtMillis = 0L;

    // State
    private long chosenDateTime = -1;
    private String eventId; // null => create

    // People & groups
    private List<PersonWithGroup> persons = new ArrayList<>();
    private List<GroupEntity> groups = new ArrayList<>();
    private final HashSet<String> selectedGuestIds = new HashSet<>();

    // helper for organizer setSelection after persons load
    private String organizerIdWanted = null;

    // Reminder options
    // 0: 5m, 1:15m, 2:1h, 3:1d, 4:1w, 5:manual
    private static final int REM_5M = 0;
    private static final int REM_15M = 1;
    private static final int REM_1H = 2;
    private static final int REM_1D = 3;
    private static final int REM_1W = 4;
    private static final int REM_MANUAL = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event);

        // ---- bind views ----
        etTitle = findViewById(R.id.etTitle);
        etAddress = findViewById(R.id.etAddress);
        etPosterUrl = findViewById(R.id.etPosterUrl);

        spOrganizer = findViewById(R.id.spOrganizer);
        btnGuests = findViewById(R.id.btnGuests);
        tvGuests = findViewById(R.id.tvGuests);

        Button btnPick = findViewById(R.id.btnPickDateTime);
        tvChosen = findViewById(R.id.tvChosenDateTime);
        Button btnSave = findViewById(R.id.btnSave);

        // Invitation UI (must exist in layout)
        spInviteTemplate = findViewById(R.id.spInviteTemplate);
        etInviteMessage = findViewById(R.id.etInviteMessage);
        swInviteMap = findViewById(R.id.swInviteMap);

        // Reminder UI (must exist in layout)
        swReminder = findViewById(R.id.swReminder);
        spReminderType = findViewById(R.id.spReminderType);
        btnPickReminderTime = findViewById(R.id.btnPickReminderTime);
        tvReminderChosen = findViewById(R.id.tvReminderChosen);

        // Title label
        eventId = getIntent().getStringExtra("eventId");
        TextView tvScreenTitle = findViewById(R.id.tvScreenTitle);
        if (tvScreenTitle != null) {
            tvScreenTitle.setText(eventId == null ? "Новое событие" : "Редактирование");
        }

        // ---- templates spinner ----
        String[] templateLabels = getResources().getStringArray(R.array.invite_template_labels);
        inviteTemplateIds = getResources().getStringArray(R.array.invite_template_ids);
        if (spInviteTemplate != null) {
            spInviteTemplate.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    templateLabels
            ));
        }
        applyInvitationUiDefaults();

        // ---- reminder spinner ----
        setupReminderUi();

        EditEventViewModel vm = new ViewModelProvider(this).get(EditEventViewModel.class);

        // ---- observe groups ----
        vm.getGroups().observe(this, list -> {
            groups = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
        });

        // ---- observe persons with group ----
        vm.getAllPersonsWithGroup().observe(this, list -> {
            persons = (list == null) ? new ArrayList<>() : new ArrayList<>(list);

            btnGuests.setEnabled(!persons.isEmpty());
            rebuildOrganizerSpinner();

            // if we already know organizerId, select it now
            if (organizerIdWanted != null) {
                int pos = findPersonIndexById(organizerIdWanted);
                if (pos >= 0) spOrganizer.setSelection(pos);
            }

            syncGuestsUi();
        });

        // ---- load event if edit ----
        if (eventId != null) {
            vm.getEvent(eventId).observe(this, e -> {
                if (e == null || e.event == null) return;
                bindEventToUi(e);
            });
        } else {
            syncGuestsUi();
            // defaults
            if (swReminder != null) swReminder.setChecked(false);
            if (spReminderType != null) spReminderType.setSelection(REM_30_DEFAULT());
            manualReminderAtMillis = 0L;
            if (tvReminderChosen != null) tvReminderChosen.setText("—");
        }

        btnPick.setOnClickListener(v -> pickDateTime());

        btnGuests.setOnClickListener(v -> {
            if (persons == null || persons.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Нет людей")
                        .setMessage("Добавь людей в разделе «Люди».")
                        .setPositiveButton("Ок", null)
                        .show();
                return;
            }
            showGroupPickerThenGuests();
        });

        btnSave.setOnClickListener(v -> onSaveClicked(vm));
    }

    // Default reminder selection: 15 minutes looks sane (humans rarely plan with 5m)
    private int REM_30_DEFAULT() {
        return REM_15M;
    }

    private void setupReminderUi() {
        if (swReminder == null || spReminderType == null || btnPickReminderTime == null || tvReminderChosen == null) {
            // layout not updated -> you will crash somewhere else anyway, but we try to be polite
            return;
        }

        String[] reminderLabels = {
                "За 5 минут",
                "За 15 минут",
                "За 1 час",
                "За 1 день",
                "За 1 неделю",
                "Вручную (выбрать дату)"
        };

        spReminderType.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                reminderLabels
        ));

        // start state
        spReminderType.setEnabled(swReminder.isChecked());
        updateManualReminderVisibility();

        spReminderType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // reset manual time when leaving manual
                if (position != REM_MANUAL) {
                    manualReminderAtMillis = 0L;
                    tvReminderChosen.setText("—");
                }
                updateManualReminderVisibility();
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        swReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spReminderType.setEnabled(isChecked);
            updateManualReminderVisibility();
        });

        btnPickReminderTime.setOnClickListener(v -> pickReminderDateTime());
    }

    private void updateManualReminderVisibility() {
        if (swReminder == null || spReminderType == null || btnPickReminderTime == null || tvReminderChosen == null) return;

        boolean enabled = swReminder.isChecked();
        boolean manual = (spReminderType.getSelectedItemPosition() == REM_MANUAL);

        btnPickReminderTime.setVisibility(enabled && manual ? View.VISIBLE : View.GONE);
        tvReminderChosen.setVisibility(enabled && manual ? View.VISIBLE : View.GONE);
    }

    private void onSaveClicked(EditEventViewModel vm) {

        // получение значений из полей ввода
        String title = safeTrim(etTitle);
        String address = safeTrim(etAddress);
        String posterUrl = safeTrim(etPosterUrl);

        // проверка обязательных полей
        if (TextUtils.isEmpty(title)) { etTitle.setError("Введите название"); return; }
        if (TextUtils.isEmpty(address)) { etAddress.setError("Введите адрес"); return; }
        if (chosenDateTime <= 0) { tvChosen.setError("Выберите дату и время"); return; }

        // генерация id события (если создаётся новое)
        final String id = (eventId != null) ? eventId : ("e_" + UUID.randomUUID());

        // получение выбранного организатора
        String organizerId = null;
        int pos = spOrganizer.getSelectedItemPosition();
        if (pos >= 0 && pos < persons.size()) {
            organizerId = persons.get(pos).id;
        }

        if (organizerId == null) {
            Toast.makeText(this, "Выберите организатора", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalOrganizerId = organizerId;
        final long finalDateTime = chosenDateTime;

        // нормализация времени (убираем миллисекунды)
        final long normalizedDateTime = (finalDateTime / 60000L) * 60000L;
        chosenDateTime = normalizedDateTime;

        // создание объекта события
        final EventEntity entity = new EventEntity(
                id,
                title,
                posterUrl.isEmpty() ? "" : posterUrl,
                address,
                normalizedDateTime,
                finalOrganizerId
        );

        // применение параметров приглашения
        applyInvitationUiToEntity(entity);

        // параметры напоминания (пока только записываем в entity)
        applyReminderUiToEntity(entity);

        final List<String> guestIds = new ArrayList<>(selectedGuestIds);

        // 1) проверка: занят ли организатор в это время
        vm.isPersonBusyAtDateTime(finalOrganizerId, finalDateTime, id, busyOrganizer -> {

            if (busyOrganizer) {
                new AlertDialog.Builder(this)
                        .setTitle("Ошибка")
                        .setMessage("Этот человек уже участвует в другом мероприятии в это же время.")
                        .setPositiveButton("Ок", null)
                        .show();
                return;
            }

            // 2) проверка: заняты ли гости в это время
            vm.checkGuestsBusyAtDateTime(id, finalDateTime, guestIds, busyIds -> {

                if (busyIds != null && !busyIds.isEmpty()) {

                    StringBuilder sb = new StringBuilder();

                    for (String pid : busyIds) {

                        String name = pid;

                        for (PersonWithGroup p : persons) {
                            if (p != null && pid.equals(p.id)) {
                                name = p.name;
                                break;
                            }
                        }

                        sb.append("• ").append(name).append("\n");
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Конфликт по времени")
                            .setMessage("Эти люди уже заняты в другом мероприятии:\n\n" + sb)
                            .setPositiveButton("Ок", null)
                            .show();

                    return;
                }

                // все проверки пройдены → сохраняем событие
                vm.saveEventWithGuests(entity, guestIds, () -> {

                    // планирование или отмена напоминания
                    if (entity.reminderEnabled) {

                        EventWithDetails fake = new EventWithDetails();
                        fake.event = entity;

                        ReminderScheduler.scheduleExact(this, fake);

                    } else {

                        ReminderScheduler.cancel(this, entity.id);
                    }

                    finish();
                });
            });
        });
    }

    private void applyReminderUiToEntity(EventEntity entity) {
        if (entity == null) return;

        if (swReminder == null || spReminderType == null) {
            entity.reminderEnabled = false;
            entity.reminderAtMillis = 0L;
            return;
        }

        boolean reminderOn = swReminder.isChecked();
        entity.reminderEnabled = reminderOn;

        if (!reminderOn) {
            entity.reminderAtMillis = 0L;
            return;
        }

        int type = spReminderType.getSelectedItemPosition();
        long triggerAt;

        if (type == REM_MANUAL) {
            triggerAt = manualReminderAtMillis;
            if (triggerAt <= 0L) {
                Toast.makeText(this, "Выбери дату напоминания", Toast.LENGTH_SHORT).show();
                // stop save, because reminder enabled but no date selected
                entity.reminderEnabled = false;
                entity.reminderAtMillis = 0L;
                return;
            }
        } else {
            long offset;
            if (type == REM_5M) offset = 5 * 60_000L;
            else if (type == REM_15M) offset = 15 * 60_000L;
            else if (type == REM_1H) offset = 60 * 60_000L;
            else if (type == REM_1D) offset = 24 * 60 * 60_000L;
            else offset = 7L * 24 * 60 * 60_000L; // week

            triggerAt = chosenDateTime - offset;
        }

        long now = System.currentTimeMillis();

        // validation: must be in future and before event
        if (triggerAt <= now || triggerAt >= chosenDateTime) {
            Toast.makeText(this, "Нельзя поставить напоминание в прошлом или после события", Toast.LENGTH_SHORT).show();
            entity.reminderEnabled = false;
            entity.reminderAtMillis = 0L;
        } else {
            entity.reminderAtMillis = triggerAt;
        }
    }

    private void bindEventToUi(EventWithDetails e) {
        EventEntity ev = e.event;

        etTitle.setText(ev.title == null ? "" : ev.title);
        etAddress.setText(ev.address == null ? "" : ev.address);
        etPosterUrl.setText(ev.posterUrl == null ? "" : ev.posterUrl);

        chosenDateTime = ev.dateTime;
        tvChosen.setError(null);
        tvChosen.setText(DateFormat.getDateTimeInstance().format(new Date(chosenDateTime)));

        organizerIdWanted = ev.organizerId;

        // guests -> ids set
        selectedGuestIds.clear();
        if (e.guests != null) {
            for (PersonEntity p : e.guests) {
                if (p != null && p.id != null) selectedGuestIds.add(p.id);
            }
        }

        // organizer selection if persons already loaded
        if (organizerIdWanted != null) {
            int pos = findPersonIndexById(organizerIdWanted);
            if (pos >= 0) spOrganizer.setSelection(pos);
        }

        // invitation -> UI
        applyInvitationEntityToUi(ev);

        // reminder -> UI
        if (swReminder != null) swReminder.setChecked(ev.reminderEnabled);
        if (tvReminderChosen != null) tvReminderChosen.setText("—");
        manualReminderAtMillis = 0L;


        if (spReminderType != null) {
            if (!ev.reminderEnabled || ev.reminderAtMillis <= 0L) {
                spReminderType.setSelection(REM_30_DEFAULT());
            } else {
                long diff = ev.dateTime - ev.reminderAtMillis;
                int preset = presetFromDiff(diff);
                if (preset >= 0) {
                    spReminderType.setSelection(preset);
                } else {
                    spReminderType.setSelection(REM_MANUAL);
                    manualReminderAtMillis = ev.reminderAtMillis;
                    if (tvReminderChosen != null) {
                        tvReminderChosen.setText(DateFormat.getDateTimeInstance().format(new Date(manualReminderAtMillis)));
                    }
                }
            }
        }

        updateManualReminderVisibility();
        syncGuestsUi();
    }

    private int presetFromDiff(long diff) {
        long m5 = 5 * 60_000L;
        long m15 = 15 * 60_000L;
        long h1 = 60 * 60_000L;
        long d1 = 24 * 60 * 60_000L;
        long w1 = 7L * 24 * 60 * 60_000L;

        // tolerate small drift (1 min) because of user edits / rounding
        long tol = 60_000L;

        if (Math.abs(diff - m5) <= tol) return REM_5M;
        if (Math.abs(diff - m15) <= tol) return REM_15M;
        if (Math.abs(diff - h1) <= tol) return REM_1H;
        if (Math.abs(diff - d1) <= tol) return REM_1D;
        if (Math.abs(diff - w1) <= tol) return REM_1W;
        return -1;
    }

    // ---------------- Organizer spinner ----------------

    private void rebuildOrganizerSpinner() {
        List<String> labels = new ArrayList<>();
        for (PersonWithGroup p : persons) labels.add(displayPerson(p));

        spOrganizer.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels
        ));
    }

    private String displayPerson(PersonWithGroup p) {
        String name = (p.name == null || p.name.trim().isEmpty()) ? "—" : p.name.trim();
        String g = (p.groupName == null || p.groupName.trim().isEmpty()) ? "Без группы" : p.groupName.trim();
        return name + " • " + g;
    }

    private int findPersonIndexById(String id) {
        if (id == null) return -1;
        for (int i = 0; i < persons.size(); i++) {
            if (id.equals(persons.get(i).id)) return i;
        }
        return -1;
    }

    // ---------------- Guests + group filter ----------------

    private void showGroupPickerThenGuests() {
        ArrayList<String> groupTitles = new ArrayList<>();
        groupTitles.add("Все");
        groupTitles.add("Без группы");
        for (GroupEntity g : groups) groupTitles.add(g.name);

        String[] arr = groupTitles.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Фильтр по группе")
                .setItems(arr, (d, which) -> showGuestsMultiChoiceForGroup(arr[which]))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showGuestsMultiChoiceForGroup(String groupTitle) {
        List<PersonWithGroup> filtered = filterPersonsByGroupTitle(groupTitle);

        if (filtered.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Пусто")
                    .setMessage("В этой группе нет людей.")
                    .setPositiveButton("Ок", null)
                    .show();
            return;
        }

        String[] labels = new String[filtered.size()];
        boolean[] checked = new boolean[filtered.size()];

        for (int i = 0; i < filtered.size(); i++) {
            PersonWithGroup p = filtered.get(i);
            labels[i] = displayPerson(p);
            checked[i] = selectedGuestIds.contains(p.id);
        }

        new AlertDialog.Builder(this)
                .setTitle("Гости: " + groupTitle)
                .setMultiChoiceItems(labels, checked, (dlg, idx, isChecked) -> {
                    PersonWithGroup p = filtered.get(idx);
                    if (isChecked) selectedGuestIds.add(p.id);
                    else selectedGuestIds.remove(p.id);
                })
                .setPositiveButton("OK", (dlg, w) -> syncGuestsUi())
                .setNeutralButton("Сменить группу", (dlg, w) -> showGroupPickerThenGuests())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private List<PersonWithGroup> filterPersonsByGroupTitle(String groupTitle) {
        ArrayList<PersonWithGroup> out = new ArrayList<>();

        if ("Все".equalsIgnoreCase(groupTitle)) {
            out.addAll(persons);
            return out;
        }

        if ("Без группы".equalsIgnoreCase(groupTitle)) {
            for (PersonWithGroup p : persons) {
                if (p.groupId == null || p.groupId.trim().isEmpty()) out.add(p);
            }
            return out;
        }

        // group by name -> id
        String gid = null;
        for (GroupEntity g : groups) {
            if (g != null && g.name != null && g.name.equalsIgnoreCase(groupTitle)) {
                gid = g.id;
                break;
            }
        }
        if (gid == null) return out;

        for (PersonWithGroup p : persons) {
            if (gid.equals(p.groupId)) out.add(p);
        }
        return out;
    }

    private void syncGuestsUi() {
        if (tvGuests == null) return;

        if (selectedGuestIds.isEmpty()) {
            tvGuests.setText("Гости: —");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (PersonWithGroup p : persons) {
            if (p != null && p.id != null && selectedGuestIds.contains(p.id)) {
                String g = (p.groupName == null || p.groupName.isEmpty()) ? "Без группы" : p.groupName;
                sb.append(p.name).append(" (").append(g).append("), ");
            }
        }

        if (sb.length() == 0) tvGuests.setText("Гости: —");
        else tvGuests.setText("Гости: " + sb.substring(0, sb.length() - 2));
    }

    // ---------------- DateTime pickers ----------------

    private void pickDateTime() {
        Calendar now = Calendar.getInstance(Locale.getDefault());

        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, day);

            new TimePickerDialog(this, (tp, hour, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);
                chosenDateTime = picked.getTimeInMillis();

                tvChosen.setError(null);
                tvChosen.setText(DateFormat.getDateTimeInstance().format(new Date(chosenDateTime)));

                // If manual reminder chosen, revalidate visibility and values
                updateManualReminderVisibility();

            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickReminderDateTime() {
        Calendar now = Calendar.getInstance(Locale.getDefault());

        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(Calendar.YEAR, year);
            picked.set(Calendar.MONTH, month);
            picked.set(Calendar.DAY_OF_MONTH, day);

            new TimePickerDialog(this, (tp, hour, minute) -> {
                picked.set(Calendar.HOUR_OF_DAY, hour);
                picked.set(Calendar.MINUTE, minute);
                picked.set(Calendar.SECOND, 0);

                manualReminderAtMillis = picked.getTimeInMillis();
                if (tvReminderChosen != null) {
                    tvReminderChosen.setText(DateFormat.getDateTimeInstance()
                            .format(new Date(manualReminderAtMillis)));
                }

            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---------------- Invitation helpers ----------------

    private void applyInvitationUiDefaults() {
        if (spInviteTemplate != null) spInviteTemplate.setSelection(0);
        if (etInviteMessage != null) etInviteMessage.setText("");
        if (swInviteMap != null) swInviteMap.setChecked(true);
    }

    private void applyInvitationEntityToUi(EventEntity ev) {
        if (ev == null) {
            applyInvitationUiDefaults();
            return;
        }

        String templateId = (ev.inviteTemplateId == null || ev.inviteTemplateId.trim().isEmpty())
                ? "classic" : ev.inviteTemplateId.trim();

        int pos = findTemplatePos(templateId);
        if (spInviteTemplate != null) spInviteTemplate.setSelection(Math.max(pos, 0));

        if (etInviteMessage != null) etInviteMessage.setText(ev.inviteMessage == null ? "" : ev.inviteMessage);

        boolean includeMap = ev.inviteIncludeMap;
        if (swInviteMap != null) swInviteMap.setChecked(includeMap);
    }

    private void applyInvitationUiToEntity(EventEntity ev) {
        if (ev == null) return;

        String templateId = "classic";
        if (spInviteTemplate != null) {
            int tpos = spInviteTemplate.getSelectedItemPosition();
            if (inviteTemplateIds != null && tpos >= 0 && tpos < inviteTemplateIds.length) {
                templateId = inviteTemplateIds[tpos];
            }
        }

        ev.inviteTemplateId = templateId;
        ev.inviteMessage = (etInviteMessage != null) ? safeTrim(etInviteMessage) : "";
        ev.inviteIncludeMap = (swInviteMap != null) ? swInviteMap.isChecked() : true;

        ev.inviteAccentColor = -1;
    }

    private int findTemplatePos(String templateId) {
        if (inviteTemplateIds == null || templateId == null) return 0;
        for (int i = 0; i < inviteTemplateIds.length; i++) {
            if (templateId.equalsIgnoreCase(inviteTemplateIds[i])) return i;
        }
        return 0;
    }

    // ---------------- misc ----------------

    private String safeTrim(EditText et) {
        if (et == null) return "";
        String s = et.getText() == null ? "" : et.getText().toString();
        return s.trim();
    }
}
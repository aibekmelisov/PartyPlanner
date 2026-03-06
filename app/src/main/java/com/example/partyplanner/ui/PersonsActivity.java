package com.example.partyplanner.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonWithGroup;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PersonsActivity extends AppCompatActivity {

    private PersonsViewModel vm;
    private ActivityResultLauncher<String[]> pickCsvLauncher;

    private EditText etSearchPeople;

    // кеш групп для фильтра
    private final List<GroupEntity> cachedGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_persons); // подключаем layout экрана людей

        setupBottomNav(); // настройка нижней навигации

        // Кнопка добавления человека
        FloatingActionButton fabAdd = findViewById(R.id.fabAddPerson);

        // RecyclerView со списком людей
        RecyclerView rv = findViewById(R.id.rvPersons);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Поле поиска
        etSearchPeople = findViewById(R.id.etSearchPeople);

        // Получаем ViewModel
        vm = new ViewModelProvider(this).get(PersonsViewModel.class);

        // Наблюдаем за списком групп (нужно для диалога фильтра)
        vm.getGroups().observe(this, groups -> {
            cachedGroups.clear();
            if (groups != null) cachedGroups.addAll(groups);
        });

        // Восстанавливаем предыдущий текст поиска из ViewModel
        etSearchPeople.setText(vm.getSearchQuery());
        etSearchPeople.setSelection(etSearchPeople.getText().length());

        // ===== debounce для поиска =====
        // Чтобы поиск не выполнялся при каждом символе
        android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];

        etSearchPeople.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                // отменяем предыдущий запрос
                if (pending[0] != null)
                    searchHandler.removeCallbacks(pending[0]);

                // откладываем новый
                pending[0] = () -> vm.setSearchQuery(s.toString());

                // задержка 250 мс
                searchHandler.postDelayed(pending[0], 250);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // ===== CSV импорт =====
        pickCsvLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    importCsv(uri); // запускаем импорт CSV
                }
        );

        // ===== Toolbar =====
        com.google.android.material.appbar.MaterialToolbar tb = findViewById(R.id.toolbarPeople);

        tb.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            // Импорт CSV файла
            if (id == R.id.action_import_csv) {

                pickCsvLauncher.launch(new String[]{
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/vnd.ms-excel"
                });

                return true;
            }

            // Показ / скрытие поиска
            if (id == R.id.action_search_people) {

                if (etSearchPeople.getVisibility() == View.GONE) {
                    etSearchPeople.setVisibility(View.VISIBLE);
                    etSearchPeople.requestFocus();
                } else {
                    etSearchPeople.setText("");
                    etSearchPeople.setVisibility(View.GONE);
                }

                return true;
            }

            // Фильтр по группам
            if (id == R.id.action_filter_group) {
                showGroupFilterDialog(cachedGroups);
                return true;
            }

            // ===== сортировка =====

            if (id == R.id.sort_name) {
                item.setChecked(true);
                vm.setSortMode(PartyRepository.PersonSort.NAME);
                return true;
            }

            if (id == R.id.sort_group) {
                item.setChecked(true);
                vm.setSortMode(PartyRepository.PersonSort.GROUP);
                return true;
            }

            if (id == R.id.sort_contacts) {
                item.setChecked(true);
                vm.setSortMode(PartyRepository.PersonSort.CONTACTS);
                return true;
            }

            // переключение направления сортировки ASC / DESC
            if (id == R.id.sort_toggle_dir) {
                vm.toggleSortDir();
                return true;
            }

            return false;
        });

        // ===== адаптер списка людей =====
        PersonsAdapter adapter = new PersonsAdapter(new PersonsAdapter.OnPersonClickListener() {

            // Нажатие на человека — открыть экран редактирования
            @Override
            public void onClick(PersonWithGroup p) {
                Intent i = new Intent(PersonsActivity.this, EditPersonActivity.class);
                i.putExtra("personId", p.id);
                startActivity(i);
            }

            // Долгое нажатие — удалить
            @Override
            public void onLongClick(PersonWithGroup p) {

                new AlertDialog.Builder(PersonsActivity.this)
                        .setTitle("Удалить?")
                        .setMessage(p.name)

                        .setPositiveButton("Удалить", (d, w) ->
                                vm.deleteSafe(p.id, canDeleted -> {

                                    // если человек используется в событиях
                                    if (!canDeleted) {
                                        new AlertDialog.Builder(PersonsActivity.this)
                                                .setTitle("Нельзя удалить")
                                                .setMessage("Этот человек используется в событиях.")
                                                .setPositiveButton("Ок", null)
                                                .show();
                                    }
                                }))

                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });

        // Подключаем адаптер
        rv.setAdapter(adapter);

        // Наблюдаем за списком людей (поиск + фильтр + сортировка)
        vm.persons().observe(this, adapter::submit);

        // Кнопка добавления нового человека
        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, EditPersonActivity.class)));
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNavPeople);
        nav.setSelectedItemId(R.id.nav_people);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_people) return true;

            if (id == R.id.nav_events) {
                Intent i = new Intent(PersonsActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });

        nav.setOnItemReselectedListener(item -> {});
    }

    private void showGroupFilterDialog(List<GroupEntity> groups) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();

        labels.add("Все");
        values.add("ALL");

        labels.add("Без группы");
        values.add("NONE");

        if (groups != null) {
            for (GroupEntity g : groups) {
                labels.add(g.name);
                values.add(g.id);
            }
        }

        String current = vm.getGroupFilter();
        int checked = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equals(current)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Фильтр по группе")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    vm.setGroupFilter(values.get(which));
                    d.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void importCsv(Uri uri) {
        List<PartyRepository.PersonImportRow> rows;
        try {
            rows = parseCsvRows(uri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_LONG).show();
            return;
        }

        if (rows.isEmpty()) {
            Toast.makeText(this, "В файле нет людей", Toast.LENGTH_LONG).show();
            return;
        }

        String[] modes = new String[] {
                "Пропускать дубликаты",
                "Перезаписать существующих"
        };

        new AlertDialog.Builder(this)
                .setTitle("Режим импорта")
                .setItems(modes, (d, which) -> {

                    PartyRepository.ImportMode mode =
                            (which == 0)
                                    ? PartyRepository.ImportMode.SKIP_DUPLICATES
                                    : PartyRepository.ImportMode.OVERWRITE_DUPLICATES;

                    vm.importPersonsWithGroups(rows, mode, report -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Импорт завершён")
                                .setMessage(
                                        "Добавлено: " + report.added +
                                                "\nОбновлено: " + report.updated +
                                                "\nПропущено дублей: " + report.skippedDuplicates +
                                                "\nОшибок строк: " + report.skippedInvalid
                                )
                                .setPositiveButton("Ок", null)
                                .show();
                    });

                })
                .setNegativeButton("Отмена", null)
                .show();

    }

    // ===== CSV parsing (как у тебя) =====

    private List<PartyRepository.PersonImportRow> parseCsvRows(Uri uri) throws Exception {
        List<PartyRepository.PersonImportRow> out = new ArrayList<>();

        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) return out;

        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        String firstLine = br.readLine();
        if (firstLine == null) return out;

        String[] first = splitCsvLine(firstLine);
        boolean hasHeader = looksLikeHeader(first);

        int idxName = 0;
        int idxContacts = 1;
        int idxPhoto = 2;
        int idxId = -1;
        int idxGroup = -1;

        if (hasHeader) {
            for (int i = 0; i < first.length; i++) {
                String h = normalizeHeader(first[i]);

                if (h.equals("name") || h.equals("fullname") || h.equals("имя") || h.equals("фио")) idxName = i;

                if (h.equals("contacts") || h.equals("contact") || h.equals("email") || h.equals("phone")
                        || h.equals("контакты") || h.equals("почта") || h.equals("телефон")) idxContacts = i;

                if (h.equals("photourl") || h.equals("photo") || h.equals("avatar") || h.equals("фото")
                        || h.equals("ссылкафото") || h.equals("аватар")) idxPhoto = i;

                if (h.equals("id")) idxId = i;

                if (h.equals("group") || h.equals("groupname") || h.equals("team") || h.equals("category")
                        || h.equals("группа") || h.equals("команда")) idxGroup = i;
            }
        } else {
            addRowFromCells(out, first, idxName, idxContacts, idxPhoto, idxId, 3);
        }

        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] row = splitCsvLine(line);
            addRowFromCells(out, row, idxName, idxContacts, idxPhoto, idxId, idxGroup);
        }

        br.close();
        return out;
    }

    private void addRowFromCells(
            List<PartyRepository.PersonImportRow> out,
            String[] row,
            int idxName, int idxContacts, int idxPhoto, int idxId, int idxGroup
    ) {
        String name = getCell(row, idxName);
        if (name == null || name.trim().isEmpty()) return;

        PartyRepository.PersonImportRow r = new PartyRepository.PersonImportRow();

        r.name = name.trim();
        r.contacts = trimOrNull(getCell(row, idxContacts));
        r.photoUrl = trimOrNull(getCell(row, idxPhoto));

        if (idxId >= 0) {
            r.id = trimOrNull(getCell(row, idxId));
        } else {
            r.id = null;
        }

        if (idxGroup >= 0) {
            String g = trimOrNull(getCell(row, idxGroup));
            if (g != null && g.equalsIgnoreCase("без группы")) g = null;
            r.groupName = g;
        } else {
            r.groupName = null;
        }

        out.add(r);
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private String getCell(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return null;
        String v = row[idx];
        if (v == null) return null;
        v = v.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    private boolean looksLikeHeader(String[] cells) {
        for (String c : cells) {
            if (c == null) continue;
            String t = normalizeHeader(c);
            if (t.equals("name") || t.equals("contacts") || t.equals("photourl") || t.equals("id")
                    || t.equals("group") || t.equals("groupname")
                    || t.equals("имя") || t.equals("контакты") || t.equals("группа")) {
                return true;
            }
        }
        return false;
    }

    private String[] splitCsvLine(String line) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '\"') {
                inQuotes = !inQuotes;
                cur.append(ch);
                continue;
            }

            if (ch == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private String normalizeHeader(String h) {
        if (h == null) return "";
        String t = h.trim().toLowerCase();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) t = t.substring(1, t.length() - 1);
        t = t.replace(" ", "").replace("_", "");
        return t;
    }
}
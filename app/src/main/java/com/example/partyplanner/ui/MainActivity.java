package com.example.partyplanner.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.util.Prefs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    // ViewModel для работы со списком событий
    private EventsViewModel vm;

    // Адаптер RecyclerView
    private EventsAdapter adapter;

    // Поле поиска
    private android.widget.EditText etSearch;

    // Флаг: показано ли сейчас поле поиска
    private boolean searchVisible = false;

    // Кэш списка групп
    private java.util.List<com.example.partyplanner.data.GroupEntity> groupsCache =
            new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // подключаем layout активности

        // Получаем ViewModel
        vm = new ViewModelProvider(this).get(EventsViewModel.class);

        // Инициализация основных частей интерфейса
        setupToolbar();
        setupBottomNav();
        setupRecycler();
        setupEmptyState();
        setupToggleAndFab();
        setupSearch();

        // Запрос разрешения на уведомления
        requestNotificationPermission();

        // Наблюдаем за списком групп
        vm.getGroups().observe(this, groups -> {
            groupsCache = (groups != null) ? groups : new java.util.ArrayList<>();
            adapter.setGroups(groups); // передаем группы в адаптер
        });

        // Наблюдаем за списком событий
        vm.getEvents().observe(this, list -> {

            // Передаем данные в адаптер
            adapter.submit(list);

            // Элементы пустого состояния
            LinearLayout emptyState = findViewById(R.id.emptyState);
            RecyclerView rv = findViewById(R.id.recycler);
            TextView tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);

            boolean isEmpty = (list == null || list.isEmpty());

            // Показываем либо пустое состояние, либо список
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

            // Меняем текст пустого состояния в зависимости от режима
            if (isEmpty) {
                if (vm.getMode() == EventsViewModel.Mode.INVITED)
                    tvEmptySubtitle.setText("Тебя ещё никуда не пригласили.");
                else
                    tvEmptySubtitle.setText("Пока нет событий. Нажми + чтобы создать первое.");
            }
        });

        // Загружаем данные с сервера
        vm.refresh();
    }

    // ================= TOOLBAR =================

    // Настройка верхней панели
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    // Создание меню toolbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_events, menu);
        return true;
    }

    // Подготовка меню перед показом
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem reminders = menu.findItem(R.id.action_reminders);
        if (reminders != null) {
            // Кнопка напоминаний видна только в режиме приглашенных событий
            reminders.setVisible(vm.getMode() == EventsViewModel.Mode.INVITED);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    // Обработка нажатий на пункты меню
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        // Показ / скрытие строки поиска
        if (id == R.id.action_search) {
            toggleSearch();
            return true;
        }

        // Диалог сортировки
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        }

        // Диалог фильтрации
        if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        }

        // Настройки напоминаний
        if (id == R.id.action_reminders) {
            showReminderSettingsDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ================= TOGGLE =================

    // Настройка переключателя режимов и FAB-кнопки добавления
    private void setupToggleAndFab() {

        MaterialButtonToggleGroup toggle = findViewById(R.id.toggleMode);
        FloatingActionButton fab = findViewById(R.id.fabAdd);

        // По умолчанию выбран режим "Все"
        toggle.check(R.id.btnAll);
        vm.setMode(EventsViewModel.Mode.ALL);

        // Обработка переключения режимов
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {

            if (!isChecked) return;

            if (checkedId == R.id.btnAll) {
                vm.setMode(EventsViewModel.Mode.ALL);
                fab.show(); // можно создавать события
            }
            else if (checkedId == R.id.btnOrg) {
                vm.setMode(EventsViewModel.Mode.ORGANIZER);
                fab.show(); // можно создавать события
            }
            else if (checkedId == R.id.btnInv) {
                vm.setMode(EventsViewModel.Mode.INVITED);
                fab.hide(); // в режиме приглашений FAB скрывается
            }

            // Обновляем меню, чтобы скрыть/показать кнопку напоминаний
            invalidateOptionsMenu();
        });

        // Кнопка создания нового события
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, EditEventActivity.class)));
    }

    // ================= RECYCLER =================

    // Настройка списка событий
    private void setupRecycler() {
        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Добавляем отступы между карточками
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect,
                                       @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                outRect.bottom = 20;
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = 20;
                }
            }
        });

        // Обработка нажатия на карточку события
        adapter = new EventsAdapter(event -> {
            Intent i = new Intent(MainActivity.this, EventDetailsActivity.class);
            i.putExtra("eventId", event.event.id);
            startActivity(i);
        });

        rv.setAdapter(adapter);
    }
    // ================= SEARCH =================

    private void setupSearch() {
        etSearch = findViewById(R.id.etSearch);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                vm.setQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void toggleSearch() {
        searchVisible = !searchVisible;
        etSearch.setVisibility(searchVisible ? View.VISIBLE : View.GONE);

        if (!searchVisible) {
            etSearch.setText("");
            vm.setQuery("");
        } else {
            etSearch.requestFocus();
        }
    }

    // ================= DIALOGS =================

    private void showSortDialog() {
        String[] items = {
                "По дате (возр.)",
                "По организатору (A-Z)",
                "По гостям (убыв.)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Сортировка")
                .setItems(items, (d, which) -> {
                    if (which == 0) vm.setSort(EventsViewModel.Sort.DATE_ASC);
                    else if (which == 1) vm.setSort(EventsViewModel.Sort.ORGANIZER_NAME);
                    else vm.setSort(EventsViewModel.Sort.GUESTS_COUNT_DESC);
                })
                .show();
    }



    private void showReminderSettingsDialog() {

        View v = getLayoutInflater().inflate(R.layout.dialog_reminder_settings, null);

        androidx.appcompat.widget.SwitchCompat sw =
                v.findViewById(R.id.swEnabled);
        android.widget.RadioGroup rg =
                v.findViewById(R.id.rgMinutes);

        boolean enabled = Prefs.isRemindersEnabled(this);
        int minutes = Prefs.getReminderMinutes(this);

        sw.setChecked(enabled);

        int checkedId;
        if (minutes == 5) checkedId = R.id.rb5;
        else if (minutes == 15) checkedId = R.id.rb15;
        else if (minutes == 60) checkedId = R.id.rb60;
        else checkedId = R.id.rb30;

        rg.check(checkedId);

        new AlertDialog.Builder(this)
                .setTitle("Напоминания")
                .setView(v)
                .setPositiveButton("Сохранить", (d, w) -> {

                    boolean newEnabled = sw.isChecked();

                    int newMinutes;
                    int id = rg.getCheckedRadioButtonId();
                    if (id == R.id.rb5) newMinutes = 5;
                    else if (id == R.id.rb15) newMinutes = 15;
                    else if (id == R.id.rb60) newMinutes = 60;
                    else newMinutes = 30;

                    Prefs.setRemindersEnabled(this, newEnabled);
                    Prefs.setReminderMinutes(this, newMinutes);

                    vm.reapply();

                    Toast.makeText(
                            this,
                            newEnabled ? "Напоминание за " + newMinutes + " мин." :
                                    "Напоминания выключены",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ================= PERMISSION =================

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1001
                );
            }
        }
    }

    // ================= EMPTY =================

    private void setupEmptyState() {
        Button btnReset = findViewById(R.id.btnResetFilters);
        btnReset.setOnClickListener(v -> {
            vm.setQuery("");
            vm.setFilter(EventsViewModel.Filter.ALL);
            vm.setSort(EventsViewModel.Sort.DATE_ASC);
            vm.setGroupFilter("ALL", false);
            etSearch.setText("");
        });
    }

    private void showFilterDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_events_filter, null);

        android.widget.RadioGroup rgTime = v.findViewById(R.id.rgTime);
        com.google.android.material.textfield.MaterialAutoCompleteTextView actGroup =
                v.findViewById(R.id.actGroup);
        android.widget.CheckBox cbOnlyOrg = v.findViewById(R.id.cbOnlyOrganizer);

        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();

        labels.add("Все"); ids.add("ALL");
        labels.add("Без группы"); ids.add("NONE");

        for (com.example.partyplanner.data.GroupEntity g : groupsCache) {
            if (g == null) continue;
            labels.add(g.name);
            ids.add(g.id);
        }

        actGroup.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels
        ));

        if (vm.getFilter() == EventsViewModel.Filter.FUTURE) rgTime.check(R.id.rbFuture);
        else if (vm.getFilter() == EventsViewModel.Filter.PAST) rgTime.check(R.id.rbPast);
        else rgTime.check(R.id.rbAll);

        cbOnlyOrg.setChecked(vm.isOnlyOrganizerForGroup());

        String currentGf = vm.getGroupFilter();
        int idx = ids.indexOf(currentGf);
        if (idx < 0) idx = 0;
        actGroup.setText(labels.get(idx), false);

        new AlertDialog.Builder(this)
                .setTitle("Фильтр событий")
                .setView(v)
                .setPositiveButton("Применить", (d, w) -> {
                    int checked = rgTime.getCheckedRadioButtonId();
                    if (checked == R.id.rbFuture) vm.setFilter(EventsViewModel.Filter.FUTURE);
                    else if (checked == R.id.rbPast) vm.setFilter(EventsViewModel.Filter.PAST);
                    else vm.setFilter(EventsViewModel.Filter.ALL);

                    String chosenLabel = actGroup.getText() != null ? actGroup.getText().toString() : "Все";
                    int pos = labels.indexOf(chosenLabel);
                    if (pos < 0) pos = 0;

                    String groupFilter = ids.get(pos);
                    boolean onlyOrg = cbOnlyOrg.isChecked();
                    vm.setGroupFilter(groupFilter, onlyOrg);
                })
                .setNeutralButton("Сброс", (d, w) -> {
                    vm.setFilter(EventsViewModel.Filter.ALL);
                    vm.setQuery("");
                    vm.setSort(EventsViewModel.Sort.DATE_ASC);
                    vm.setGroupFilter("ALL", false);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }


    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_events);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_events) return true;

            if (id == R.id.nav_people) {
                Intent i = new Intent(MainActivity.this, PersonsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                overridePendingTransition(0, 0);
                finish();
                return true;
            }

            return false;
        });

        nav.setOnItemReselectedListener(item -> { });
    }
}
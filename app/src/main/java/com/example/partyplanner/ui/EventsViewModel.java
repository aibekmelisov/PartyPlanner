package com.example.partyplanner.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonEntity;
import com.example.partyplanner.reminders.ReminderScheduler;
import com.example.partyplanner.util.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EventsViewModel extends AndroidViewModel {

    // Режим отображения событий
    public enum Mode { ALL, ORGANIZER, INVITED }

    // Варианты сортировки
    public enum Sort { DATE_ASC, ORGANIZER_NAME, GUESTS_COUNT_DESC }

    // Фильтр по времени
    public enum Filter { ALL, FUTURE, PAST }

    // Репозиторий для работы с данными
    private final PartyRepository repo;

    // Итоговый список событий, который наблюдает UI
    private final MediatorLiveData<List<EventWithDetails>> events = new MediatorLiveData<>();

    // Текущий источник данных LiveData
    private LiveData<List<EventWithDetails>> source;

    // Последний "сырой" список без фильтрации и сортировки
    private List<EventWithDetails> lastRaw = new ArrayList<>();

    // Текущие настройки отображения
    private Mode mode = Mode.ALL;
    private Sort sort = Sort.DATE_ASC;
    private Filter filter = Filter.ALL;
    private String query = "";

    // Фильтр по группе: "ALL" | "NONE" | конкретный groupId
    private String groupFilter = "ALL";

    // false -> фильтрация по гостям
    // true  -> фильтрация только по организатору
    private boolean onlyOrganizerForGroup = false;

    // Базовый URL mock API
    private static final String BASE_URL =
            "https://c5b41663-48d3-4135-8955-24fca9bfe475.mock.pstmn.io/";

    public EventsViewModel(@NonNull Application application) {
        super(application);
        repo = new PartyRepository(application, BASE_URL);
        setMode(Mode.ALL); // по умолчанию показываем все события
    }

    // Возвращает готовый список событий для UI
    public LiveData<List<EventWithDetails>> getEvents() {
        return events;
    }

    // Обновление данных с сервера
    public void refresh() {
        repo.refreshFromNetwork();
    }

    // Смена режима просмотра событий
    public void setMode(Mode mode) {
        this.mode = mode;
        switchSource();
    }

    // Смена сортировки
    public void setSort(Sort sort) {
        this.sort = sort;
        applyTransform();
    }

    // Смена фильтра по времени
    public void setFilter(Filter filter) {
        this.filter = filter;
        applyTransform();
    }

    // Поисковый запрос
    public void setQuery(String q) {
        this.query = (q == null) ? "" : q.trim().toLowerCase();
        applyTransform();
    }

    public Mode getMode() { return mode; }
    public Sort getSort() { return sort; }
    public Filter getFilter() { return filter; }

    // Получение списка групп для dropdown/spinner в UI
    public LiveData<List<GroupEntity>> getGroups() {
        return repo.observeAllGroups();
    }

    // Текущие настройки фильтра по группе
    public String getGroupFilter() { return groupFilter; }
    public boolean isOnlyOrganizerForGroup() { return onlyOrganizerForGroup; }

    // Установка фильтра по группе
    public void setGroupFilter(String gf, boolean onlyOrganizer) {
        this.groupFilter = (gf == null || gf.trim().isEmpty()) ? "ALL" : gf;
        this.onlyOrganizerForGroup = onlyOrganizer;
        applyTransform();
    }

    // Переключение источника данных в зависимости от режима
    private void switchSource() {
        if (source != null) events.removeSource(source);

        String me = Prefs.getCurrentUserId(getApplication());

        if (mode == Mode.ORGANIZER) {
            source = repo.observeOrganizerEvents(me); // события, где я организатор
        } else if (mode == Mode.INVITED) {
            source = repo.observeInvitedEvents(me);   // события, куда я приглашен
        } else {
            source = repo.observeAllEvents();         // все события
        }

        // При изменении источника обновляем "сырой" список и применяем фильтры
        events.addSource(source, list -> {
            lastRaw = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
            applyTransform();
        });
    }

    // Основная логика: фильтрация, поиск, сортировка, напоминания
    private void applyTransform() {
        List<EventWithDetails> out = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (EventWithDetails e : lastRaw) {
            if (e == null || e.event == null) continue;

            // Фильтр по времени
            if (filter == Filter.FUTURE && e.event.dateTime < now) continue;
            if (filter == Filter.PAST && e.event.dateTime >= now) continue;

            // Поиск по названию и адресу
            if (!query.isEmpty()) {
                String t = safeLower(e.event.title);
                String a = safeLower(e.event.address);
                if (!t.contains(query) && !a.contains(query)) continue;
            }

            // Фильтр по группе
            if (!"ALL".equals(groupFilter)) {
                boolean ok = false;

                if (onlyOrganizerForGroup) {
                    // Фильтрация по группе организатора
                    String og = (e.organizer != null) ? e.organizer.groupId : null;

                    if ("NONE".equals(groupFilter)) {
                        ok = (og == null || og.trim().isEmpty());
                    } else {
                        ok = (og != null && og.equals(groupFilter));
                    }
                } else {
                    // Фильтрация по группе среди гостей
                    if (e.guests != null) {
                        for (PersonEntity p : e.guests) {
                            if (p == null) continue;
                            String g = p.groupId;

                            if ("NONE".equals(groupFilter)) {
                                if (g == null || g.trim().isEmpty()) {
                                    ok = true;
                                    break;
                                }
                            } else {
                                if (g != null && g.equals(groupFilter)) {
                                    ok = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!ok) continue;
            }

            out.add(e);
        }

        // Сортировка итогового списка
        if (sort == Sort.DATE_ASC) {
            Collections.sort(out, Comparator.comparingLong(o -> o.event.dateTime));
        } else if (sort == Sort.ORGANIZER_NAME) {
            Collections.sort(out, (a, b) -> safeLowerName(a).compareTo(safeLowerName(b)));
        } else if (sort == Sort.GUESTS_COUNT_DESC) {
            Collections.sort(out, (a, b) -> Integer.compare(guestCount(b), guestCount(a)));
        }

        // Напоминания ставим только для режима "Приглашён"
        if (mode == Mode.INVITED) {
            boolean enabled = Prefs.isRemindersEnabled(getApplication());
            if (enabled) {
                int minutes = Prefs.getReminderMinutes(getApplication());
                for (EventWithDetails e : out) {
                    ReminderScheduler.scheduleExact(getApplication(), e);
                }
            } else {
                // Если напоминания выключены — отменяем их
                for (EventWithDetails e : out) {
                    if (e != null && e.event != null && e.event.id != null) {
                        com.example.partyplanner.reminders.ReminderScheduler.cancel(
                                getApplication(),
                                e.event.id
                        );
                    }
                }
            }
        }

        // Отправляем итоговый список в UI
        events.setValue(out);
    }

    // Безопасное приведение строки к lower case
    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    // Безопасное получение имени организатора в lower case
    private String safeLowerName(EventWithDetails e) {
        if (e == null || e.organizer == null || e.organizer.name == null) return "";
        return e.organizer.name.toLowerCase();
    }

    // Подсчет количества гостей
    private int guestCount(EventWithDetails e) {
        return (e != null && e.guests != null) ? e.guests.size() : 0;
    }

    // Повторное применение всех фильтров и сортировки
    public void reapply() {
        applyTransform();
    }
}
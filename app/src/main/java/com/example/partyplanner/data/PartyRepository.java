package com.example.partyplanner.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.partyplanner.data.api.ApiService;
import com.example.partyplanner.data.api.EventDto;
import com.example.partyplanner.data.api.PersonDto;
import com.example.partyplanner.data.api.RetrofitClient;
import com.example.partyplanner.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.partyplanner.data.firebase.FirebaseRsvpManager;
import com.google.firebase.firestore.ListenerRegistration;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PartyRepository {

    // База данных Room
    private final AppDatabase db;

    // API для загрузки данных с сервера
    private final ApiService api;

    // Firebase менеджер для работы с RSVP
    private final FirebaseRsvpManager firebase = new FirebaseRsvpManager();

    // Executor для выполнения операций в фоне (чтобы не блокировать UI поток)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Handler для возврата результата обратно в главный поток (UI)
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // Конструктор репозитория
    public PartyRepository(Context context, String baseUrl) {
        db = AppDatabase.getInstance(context); // получаем экземпляр базы данных
        api = RetrofitClient.api(baseUrl);     // создаем API клиент
    }

    // Коды результата добавления гостя
    public static final int ADD_OK = 1;                     // гость успешно добавлен
    public static final int ADD_DUPLICATE_SAME_EVENT = 0;   // гость уже есть в этом событии
    public static final int ADD_BUSY_OTHER_EVENT = -1;      // гость занят в другом событии в это же время


    // Добавление одного гостя с проверкой конфликта времени
    public void addGuestCheckedWithTimeConflict(String eventId, String personId,
                                                java.util.function.Consumer<Integer> onDone) {
        executor.execute(() -> {

            // 1. Проверяем: есть ли у человека другое событие в это же время
            int busy = db.eventDao().countPersonBusyAtEventTime(personId, eventId);
            if (busy > 0) {
                // если занят — возвращаем результат
                mainHandler.post(() -> onDone.accept(ADD_BUSY_OTHER_EVENT));
                return;
            }

            // 2. Пытаемся добавить гостя в текущее событие
            long res = db.eventDao().addGuest(new EventGuestCrossRef(eventId, personId, "INVITED"));

            // если insert вернул -1 → это дубликат
            int code = (res == -1) ? ADD_DUPLICATE_SAME_EVENT : ADD_OK;

            // возвращаем результат в UI поток
            mainHandler.post(() -> onDone.accept(code));
        });
    }


    // Отчет массового добавления гостей
    public static class AddGuestsReport2 {
        public int added;              // сколько добавлено
        public int skippedDuplicates;  // сколько пропущено из-за дубликатов
        public int skippedBusy;        // сколько пропущено из-за конфликта времени
    }


    // Массовое добавление гостей с проверкой конфликтов времени
    public void addGuestsBulkCheckedWithTimeConflict(String eventId, List<String> personIds,
                                                     java.util.function.Consumer<AddGuestsReport2> onDone) {
        executor.execute(() -> {

            AddGuestsReport2 r = new AddGuestsReport2();

            // если список пуст — просто возвращаем пустой отчет
            if (eventId == null || personIds == null || personIds.isEmpty()) {
                mainHandler.post(() -> onDone.accept(r));
                return;
            }

            // Убираем дубликаты из входного списка
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String pid : personIds)
                if (pid != null && !pid.trim().isEmpty())
                    set.add(pid.trim());

            List<String> ids = new ArrayList<>(set);

            // 1. Находим людей, занятых в другое событие на это же время
            List<String> busyIds = db.eventDao().findBusyPersonsAtEventTime(ids, eventId);
            java.util.HashSet<String> busySet = new java.util.HashSet<>(busyIds);

            // 2. Подготавливаем список для вставки (только свободные люди)
            List<EventGuestCrossRef> refs = new ArrayList<>();

            for (String pid : ids) {
                if (busySet.contains(pid)) continue; // пропускаем занятых
                refs.add(new EventGuestCrossRef(eventId, pid, "INVITED"));
            }

            // выполняем массовую вставку
            long[] res = refs.isEmpty() ? new long[0] : db.eventDao().addGuests(refs);

            // анализируем результаты вставки
            for (long x : res) {
                if (x == -1) r.skippedDuplicates++; // уже был добавлен
                else r.added++;                     // успешно добавлен
            }

            // количество занятых людей
            r.skippedBusy = busySet.size();

            // возвращаем отчет в UI поток
            mainHandler.post(() -> onDone.accept(r));
        });
    }


    // Проверка заняты ли выбранные гости в конкретное время (например при сохранении события)
    public void checkGuestsBusyAtDateTime(String excludeEventId, long dateTime, List<String> guestIds,
                                          java.util.function.Consumer<List<String>> onDone) {

        executor.execute(() -> {

            // если гостей нет — сразу возвращаем пустой список
            if (guestIds == null || guestIds.isEmpty()) {
                mainHandler.post(() -> onDone.accept(new ArrayList<>()));
                return;
            }

            // ищем гостей, занятых в это время
            List<String> busy = db.eventDao().findBusyPersonsAtDateTime(guestIds, dateTime, excludeEventId);

            // возвращаем результат
            mainHandler.post(() ->
                    onDone.accept(busy == null ? new ArrayList<>() : busy));
        });
    }


    // ===== Сортировка списка людей =====

    // поле сортировки
    public enum PersonSort {
        NAME,      // по имени
        GROUP,     // по группе
        CONTACTS   // по контактам
    }

    // направление сортировки
    public enum SortDir {
        ASC,   // по возрастанию
        DESC   // по убыванию
    }


    // наблюдение за всеми событиями (LiveData)
    public LiveData<List<EventWithDetails>> observeAllEvents() {
        return db.eventDao().observeAllEventsWithDetails();
    }


    // загрузка событий с сервера
    public void refreshFromNetwork() {

        api.getEvents().enqueue(new Callback<List<EventDto>>() {

            @Override
            public void onResponse(Call<List<EventDto>> call, Response<List<EventDto>> response) {

                // если ответ не успешный — выходим
                if (!response.isSuccessful() || response.body() == null) return;

                List<EventDto> dtos = response.body();

                // выполняем сохранение в фоне
                AppExecutors.io().execute(() -> {

                    List<PersonEntity> persons = new ArrayList<>();
                    List<EventEntity> events = new ArrayList<>();
                    List<EventGuestCrossRef> refs = new ArrayList<>();

                    for (EventDto e : dtos) {

                        String organizerId = null;

                        // если есть организатор
                        if (e.organizer != null) {
                            organizerId = e.organizer.id;
                            persons.add(mapPerson(e.organizer));
                        }

                        // создаем сущность события
                        events.add(new EventEntity(
                                e.id,
                                e.title,
                                e.posterUrl,
                                e.address,
                                e.dateTime,
                                organizerId
                        ));

                        // добавляем гостей
                        if (e.guests != null) {
                            for (PersonDto g : e.guests) {
                                persons.add(mapPerson(g));
                                refs.add(new EventGuestCrossRef(e.id, g.id));
                            }
                        }
                    }

                    // сохраняем данные в базу
                    db.personDao().upsertAll(dedupPersons(persons));
                    db.eventDao().upsertAllEvents(events);
                    db.eventDao().upsertCrossRefs(refs);
                });
            }

            @Override
            public void onFailure(Call<List<EventDto>> call, Throwable t) {
                t.printStackTrace(); // вывод ошибки
            }
        });
    }


    private PersonEntity mapPerson(PersonDto dto) {
        return new PersonEntity(dto.id, dto.name, dto.photoUrl, dto.contacts, null);
    }

    private List<PersonEntity> dedupPersons(List<PersonEntity> list) {
        java.util.HashMap<String, PersonEntity> map = new java.util.HashMap<>();
        for (PersonEntity p : list) {
            if (p != null && p.id != null) map.put(p.id, p);
        }
        return new ArrayList<>(map.values());
    }

    public LiveData<List<EventWithDetails>> observeOrganizerEvents(String personId) {
        return db.eventDao().observeMineAsOrganizer(personId);
    }

    public LiveData<List<EventWithDetails>> observeInvitedEvents(String personId) {
        return db.eventDao().observeInvited(personId);
    }

    public LiveData<EventWithDetails> observeEvent(String id) {
        return db.eventDao().observeById(id);
    }

    public void deleteEvent(String id) {
        executor.execute(() -> {
            db.eventDao().deleteGuestsByEventId(id);
            db.eventDao().deleteById(id);
        });
    }

    public void saveEvent(EventEntity event) {
        executor.execute(() -> db.eventDao().upsertEvent(event));
    }

    public LiveData<List<PersonEntity>> observeAllPersons() {
        return db.personDao().observeAll();
    }

    public void savePerson(PersonEntity p) {
        executor.execute(() -> db.personDao().upsert(p));
    }

    public void deletePerson(String id) {
        executor.execute(() -> {
            db.eventDao().deleteGuestLinks(id);
            db.personDao().deleteById(id);
        });
    }

    public void addGuest(String eventId, String personId) {
        executor.execute(() -> db.eventDao().addGuest(new EventGuestCrossRef(eventId, personId, "INVITED")));
    }

    public void removeGuest(String eventId, String personId) {
        executor.execute(() -> db.eventDao().removeGuest(eventId, personId));
    }

    public void replaceGuests(String eventId, java.util.List<String> guestIds) {
        executor.execute(() -> {
            db.eventDao().deleteGuestsByEventId(eventId);

            if (guestIds == null || guestIds.isEmpty()) return;

            List<EventGuestCrossRef> refs = new ArrayList<>();
            for (String pid : guestIds) refs.add(new EventGuestCrossRef(eventId, pid));
            db.eventDao().addGuests(refs);
        });
    }

    public boolean canDeletePersonSync(String personId) {
        int org = db.eventDao().countOrganizerEvents(personId);
        int guest = db.eventDao().countGuestLinks(personId);
        return org == 0 && guest == 0;
    }

    public void deletePersonSafe(String personId, java.util.function.Consumer<Boolean> callback) {
        executor.execute(() -> {
            boolean can = canDeletePersonSync(personId);
            if (can) {
                db.eventDao().deletePerson(personId);
            }
            mainHandler.post(() -> callback.accept(can));
        });
    }

    public static class PersonImportRow {
        public String id;
        public String name;
        public String photoUrl;
        public String contacts;
        public String groupName;
    }

    public enum ImportMode {
        SKIP_DUPLICATES,
        OVERWRITE_DUPLICATES
    }

    public static class ImportReport {
        public int added;
        public int updated;
        public int skippedDuplicates;
        public int skippedInvalid;
    }

    public void importPersonsWithGroups(List<PersonImportRow> rows,
                                        ImportMode mode,
                                        java.util.function.Consumer<ImportReport> onDone) {

        executor.execute(() -> {
            ImportReport report = new ImportReport();

            try {
                if (rows != null && !rows.isEmpty()) {

                    List<PersonEntity> toUpsert = new ArrayList<>();

                    for (PersonImportRow r : rows) {

                        if (r == null || r.name == null || r.name.trim().isEmpty()) {
                            report.skippedInvalid++;
                            continue;
                        }

                        String name = r.name.trim();

                        String photo = (r.photoUrl != null && !r.photoUrl.trim().isEmpty())
                                ? r.photoUrl.trim()
                                : null;

                        String contacts = (r.contacts != null && !r.contacts.trim().isEmpty())
                                ? r.contacts.trim()
                                : null;

                        String gid = ensureGroupIdSync(r.groupName);

                        // Если контакта нет, дубликаты не проверяем (разрешаем)
                        if (contacts == null) {
                            String id = (r.id != null && !r.id.trim().isEmpty())
                                    ? r.id.trim()
                                    : "p_" + java.util.UUID.randomUUID();

                            toUpsert.add(new PersonEntity(id, name, photo, null, gid));
                            report.added++;
                            continue;
                        }

                        // Есть контакт: проверяем существующего
                        String existingId = db.personDao().findIdByContacts(contacts);

                        if (existingId != null) {
                            // Дубликат найден
                            if (mode == ImportMode.SKIP_DUPLICATES) {
                                report.skippedDuplicates++;
                                continue;
                            } else {
                                // OVERWRITE: обновляем существующего
                                toUpsert.add(new PersonEntity(existingId, name, photo, contacts, gid));
                                report.updated++;
                                continue;
                            }
                        }

                        // Новая запись
                        String id = (r.id != null && !r.id.trim().isEmpty())
                                ? r.id.trim()
                                : "p_" + java.util.UUID.randomUUID();

                        toUpsert.add(new PersonEntity(id, name, photo, contacts, gid));
                        report.added++;
                    }

                    if (!toUpsert.isEmpty()) {
                        db.personDao().upsertAll(toUpsert);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            mainHandler.post(() -> onDone.accept(report));
        });
    }

    public LiveData<List<GroupEntity>> observeAllGroups() {
        return db.groupDao().observeAll();
    }

    private String ensureGroupIdSync(String groupName) {
        if (groupName == null) return null;
        String n = groupName.trim();
        if (n.isEmpty()) return null;

        String existing = db.groupDao().findIdByNameSync(n);
        if (existing != null) return existing;

        String id = "g_" + java.util.UUID.randomUUID();
        db.groupDao().upsert(new GroupEntity(id, n));
        return id;
    }

    public void savePersonWithGroupName(PersonEntity p,
                                        String groupName,
                                        java.util.function.Consumer<Boolean> callback) {

        executor.execute(() -> {
            try {

                // --- нормализация контакта ---
                String contacts = null;
                if (p.contacts != null) {
                    contacts = p.contacts.trim();
                    if (contacts.isEmpty()) contacts = null;
                }

                // --- проверка дубля ---
                if (contacts != null) {
                    int exists = db.personDao()
                            .existsByContacts(contacts, p.id);
                    if (exists > 0) {
                        mainHandler.post(() -> callback.accept(false));
                        return;
                    }
                }

                // --- группа ---
                String gid = ensureGroupIdSync(groupName);

                PersonEntity fixed = new PersonEntity(
                        p.id,
                        p.name,
                        p.photoUrl,
                        contacts,
                        gid
                );

                db.personDao().upsert(fixed);

                mainHandler.post(() -> callback.accept(true));

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.accept(false));
            }
        });
    }

    public void addGuestsBulk(String eventId, List<String> personIds) {
        executor.execute(() -> {
            if (eventId == null || personIds == null || personIds.isEmpty()) return;

            List<EventGuestCrossRef> refs = new ArrayList<>();
            for (String pid : personIds) {
                if (pid == null || pid.trim().isEmpty()) continue;
                refs.add(new EventGuestCrossRef(eventId, pid, "INVITED"));
            }
            db.eventDao().addGuests(refs);
        });
    }

    public void importPersons(List<PersonEntity> persons,
                              java.util.function.Consumer<Integer> onDone) {
        executor.execute(() -> {
            int count = 0;
            try {
                if (persons != null && !persons.isEmpty()) {
                    db.personDao().upsertAll(persons);
                    count = persons.size();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            int finalCount = count;
            mainHandler.post(() -> onDone.accept(finalCount));
        });
    }

    // ====== NEW: one source for list with search + group filter + sorting ======
    // groupFilter: "ALL" | "NONE" | groupId
    public LiveData<List<PersonWithGroup>> observePersonsWithGroup(
            String query,
            String groupFilter,
            PersonSort sort,
            SortDir dir
    ) {
        String q = (query == null) ? "" : query.trim().toLowerCase();
        String like = "%" + q + "%";

        String gf = (groupFilter == null || groupFilter.trim().isEmpty()) ? "ALL" : groupFilter;

        if (sort == null) sort = PersonSort.NAME;
        if (dir == null) dir = SortDir.ASC;

        switch (sort) {
            case GROUP:
                return (dir == SortDir.ASC)
                        ? db.personDao().observeFilteredByGroupSortGroupAsc(like, gf)
                        : db.personDao().observeFilteredByGroupSortGroupDesc(like, gf);

            case CONTACTS:
                return (dir == SortDir.ASC)
                        ? db.personDao().observeFilteredByGroupSortContactsAsc(like, gf)
                        : db.personDao().observeFilteredByGroupSortContactsDesc(like, gf);

            case NAME:
            default:
                return (dir == SortDir.ASC)
                        ? db.personDao().observeFilteredByGroupSortNameAsc(like, gf)
                        : db.personDao().observeFilteredByGroupSortNameDesc(like, gf);
        }
    }

    public LiveData<List<PersonWithGroup>> observePersonListDefault() {
        return observePersonsWithGroup("", "ALL", PersonSort.NAME, SortDir.ASC);
    }

    public LiveData<PersonWithGroup> observePersonWithGroup(String id) {
        return db.personDao().observeOneWithGroup(id);
    }

    public LiveData<List<PersonWithGroup>> observeAllPersonsWithGroup() {
        return db.eventDao().observeAllPersonsWithGroup();
    }

    public void updateGuestStatus(String eventId, String personId, String status) {
        executor.execute(() -> {
            db.eventDao().updateGuestStatus(eventId, personId, status);
            firebase.updateStatus(eventId, personId, status);
        });
    }

    public LiveData<List<GuestWithStatus>> observeGuestsWithStatus(String eventId) {
        return db.eventDao().observeGuestsWithStatus(eventId);
    }
    public ListenerRegistration observeRsvpRealtime(
            String eventId
    ) {
        return firebase.listenForGuestUpdates(eventId, (personId, status) -> {
            executor.execute(() ->
                    db.eventDao().updateGuestStatus(eventId, personId, status)
            );
        });
    }

    public void addGuestChecked(String eventId, String personId, java.util.function.Consumer<Boolean> onDone) {
        executor.execute(() -> {
            long res = db.eventDao().addGuest(new EventGuestCrossRef(eventId, personId, "INVITED"));
            boolean added = res != -1;
            mainHandler.post(() -> onDone.accept(added));
        });
    }

    public static class AddGuestsReport {
        public int added;
        public int skippedDuplicates;
    }

    public void addGuestsBulkChecked(String eventId, List<String> personIds,
                                     java.util.function.Consumer<AddGuestsReport> onDone) {
        executor.execute(() -> {
            AddGuestsReport r = new AddGuestsReport();
            if (eventId == null || personIds == null || personIds.isEmpty()) {
                mainHandler.post(() -> onDone.accept(r));
                return;
            }

            // дедуп на всякий случай
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String pid : personIds) {
                if (pid != null && !pid.trim().isEmpty()) set.add(pid.trim());
            }

            List<EventGuestCrossRef> refs = new ArrayList<>();
            for (String pid : set) refs.add(new EventGuestCrossRef(eventId, pid, "INVITED"));

            long[] res = db.eventDao().addGuests(refs);
            for (long x : res) {
                if (x == -1) r.skippedDuplicates++;
                else r.added++;
            }

            mainHandler.post(() -> onDone.accept(r));
        });
    }

    public void isPersonBusyAtDateTime(String personId, long dateTime, String excludeEventId,
                                       java.util.function.Consumer<Boolean> onDone) {
        executor.execute(() -> {
            String ex = (excludeEventId == null) ? "" : excludeEventId;
            int c = db.eventDao().countPersonBusyAtDateTime(personId, dateTime, ex);
            boolean busy = c > 0;
            mainHandler.post(() -> onDone.accept(busy));
        });
    }

    public void createInviteForGuest(EventWithDetails e, String personId,
                                     java.util.function.Consumer<String> onDone) {
        firebase.createInvite(e, personId, onDone);
    }

    public void getRsvpStats(String eventId, java.util.function.Consumer<RsvpStats> onDone) {
        executor.execute(() -> {
            RsvpStats s = new RsvpStats();
            try {
                s.going = db.eventDao().countGuestsByStatus(eventId, "GOING");
                s.maybe = db.eventDao().countGuestsByStatus(eventId, "MAYBE");
                s.declined = db.eventDao().countGuestsByStatus(eventId, "DECLINED");
                s.invited = db.eventDao().countGuestsByStatus(eventId, "INVITED");
            } catch (Exception e) {
                e.printStackTrace();
            }
            mainHandler.post(() -> onDone.accept(s));
        });
    }

    public void saveEventWithGuests(EventEntity event, List<String> guestIds, Runnable onDone) {
        executor.execute(() -> {
            try {
                db.runInTransaction(() -> {
                    db.eventDao().upsertEvent(event);

                    // важно: сначала чистим, потом вставляем
                    db.eventDao().deleteGuestsByEventId(event.id);

                    if (guestIds != null && !guestIds.isEmpty()) {
                        List<EventGuestCrossRef> refs = new ArrayList<>();
                        for (String pid : guestIds) {
                            if (pid == null || pid.trim().isEmpty()) continue;
                            refs.add(new EventGuestCrossRef(event.id, pid, "INVITED"));
                        }
                        if (!refs.isEmpty()) db.eventDao().addGuests(refs);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (onDone != null) {
                mainHandler.post(onDone);
            }
        });
    }


}
package com.example.partyplanner.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonEntity;
import com.example.partyplanner.data.PersonWithGroup;

import java.util.List;

public class PersonsViewModel extends AndroidViewModel {

    // Репозиторий для работы с людьми и группами
    private final PartyRepository repo;

    // Базовый URL mock API
    private static final String BASE_URL =
            "https://c5b41663-48d3-4135-8955-24fca9bfe475.mock.pstmn.io/";

    // Параметры списка пользователей

    // Поисковый запрос
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    // Фильтр по группе: "ALL" | "NONE" | конкретный groupId
    private final MutableLiveData<String> groupFilter = new MutableLiveData<>("ALL");

    // Поле сортировки
    private final MutableLiveData<PartyRepository.PersonSort> sortMode =
            new MutableLiveData<>(PartyRepository.PersonSort.NAME);

    // Направление сортировки
    private final MutableLiveData<PartyRepository.SortDir> sortDir =
            new MutableLiveData<>(PartyRepository.SortDir.ASC);

    // Итоговый список людей для UI
    private final MediatorLiveData<List<PersonWithGroup>> persons = new MediatorLiveData<>();

    // Текущий источник данных
    private LiveData<List<PersonWithGroup>> currentSource;

    public PersonsViewModel(@NonNull Application app) {
        super(app);
        repo = new PartyRepository(app, BASE_URL);

        // При изменении любого параметра обновляем источник данных
        persons.addSource(searchQuery, s -> refreshSource());
        persons.addSource(groupFilter, s -> refreshSource());
        persons.addSource(sortMode, s -> refreshSource());
        persons.addSource(sortDir, s -> refreshSource());

        // Первичная загрузка списка
        refreshSource();
    }

    // Пересоздает источник данных в зависимости от поиска, фильтра и сортировки
    private void refreshSource() {
        String q = searchQuery.getValue();
        String gf = groupFilter.getValue();
        PartyRepository.PersonSort sm = sortMode.getValue();
        PartyRepository.SortDir sd = sortDir.getValue();

        // Получаем новый LiveData-источник из репозитория
        LiveData<List<PersonWithGroup>> newSource =
                repo.observePersonsWithGroup(q, gf, sm, sd);

        // Удаляем старый источник, чтобы не было лишних наблюдателей
        if (currentSource != null) persons.removeSource(currentSource);

        currentSource = newSource;

        // Подключаем новый источник
        persons.addSource(currentSource, persons::setValue);
    }

    // ===== Вывод списка =====

    // Возвращает список людей для отображения в UI
    public LiveData<List<PersonWithGroup>> persons() {
        return persons;
    }

    // ===== Поиск =====

    // Устанавливает поисковый запрос
    public void setSearchQuery(String q) {
        searchQuery.setValue(q == null ? "" : q);
    }

    // Возвращает текущий поисковый запрос
    public String getSearchQuery() {
        String v = searchQuery.getValue();
        return v == null ? "" : v;
    }

    // ===== Фильтр по группе =====

    // Устанавливает фильтр группы
    public void setGroupFilter(String filter) {
        groupFilter.setValue(filter == null ? "ALL" : filter);
    }

    // Возвращает текущий фильтр группы
    public String getGroupFilter() {
        String v = groupFilter.getValue();
        return v == null ? "ALL" : v;
    }

    // ===== Сортировка =====

    // Устанавливает режим сортировки
    public void setSortMode(PartyRepository.PersonSort mode) {
        sortMode.setValue(mode);
    }

    // Переключает направление сортировки ASC <-> DESC
    public void toggleSortDir() {
        PartyRepository.SortDir cur = sortDir.getValue();
        sortDir.setValue(cur == PartyRepository.SortDir.ASC
                ? PartyRepository.SortDir.DESC
                : PartyRepository.SortDir.ASC);
    }

    // ===== Остальные методы работы с данными =====

    // Получение всех людей без дополнительных фильтров
    public LiveData<List<PersonEntity>> getAll() {
        return repo.observeAllPersons();
    }

    // Сохранение человека
    public void save(PersonEntity p) {
        repo.savePerson(p);
    }

    // Удаление человека по id
    public void delete(String id) {
        repo.deletePerson(id);
    }

    // Безопасное удаление человека
    // callback = true, если удаление прошло успешно
    public void deleteSafe(String id, java.util.function.Consumer<Boolean> callback) {
        repo.deletePersonSafe(id, callback);
    }

    // Получение всех групп
    public LiveData<List<GroupEntity>> getGroups() {
        return repo.observeAllGroups();
    }

    // Получение одного человека вместе с названием его группы
    public LiveData<PersonWithGroup> getOneWithGroup(String id) {
        return repo.observePersonWithGroup(id);
    }

    // Сохранение человека, при необходимости с созданием/поиском группы по имени
    public void savePersonWithGroupName(PersonEntity p,
                                        String groupName,
                                        java.util.function.Consumer<Boolean> callback) {
        repo.savePersonWithGroupName(p, groupName, callback);
    }

    // Импорт людей и групп из подготовленных строк
    public void importPersonsWithGroups(
            List<PartyRepository.PersonImportRow> rows,
            PartyRepository.ImportMode mode,
            java.util.function.Consumer<PartyRepository.ImportReport> onDone
    ) {
        repo.importPersonsWithGroups(rows, mode, onDone);
    }
}
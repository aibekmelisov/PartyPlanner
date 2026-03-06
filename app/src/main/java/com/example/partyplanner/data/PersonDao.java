package com.example.partyplanner.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PersonDao {

    // добавление или обновление списка пользователей
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PersonEntity> persons);

    // добавление или обновление одного пользователя
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PersonEntity person);

    // обновление существующего пользователя
    @Update
    void update(PersonEntity person);

    // удаление пользователя по идентификатору
    @Query("DELETE FROM persons WHERE id = :id")
    void deleteById(String id);

    // получение списка всех пользователей
    @Query("SELECT * FROM persons ORDER BY name")
    LiveData<List<PersonEntity>> observeAll();

    // получение одного пользователя вместе с названием его группы
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE p.id = :id LIMIT 1")
    LiveData<PersonWithGroup> observeOneWithGroup(String id);

    // ===== ПОИСК + ФИЛЬТР ГРУППЫ + СОРТИРОВКА =====
    // groupFilter: ALL | NONE | конкретный groupId
    // q — строка поиска (LIKE pattern)

    // сортировка по имени (по возрастанию)
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY p.name COLLATE NOCASE ASC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortNameAsc(String q, String groupFilter);

    // сортировка по имени (по убыванию)
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY p.name COLLATE NOCASE DESC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortNameDesc(String q, String groupFilter);

    // сортировка по названию группы
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY IFNULL(g.name,'') COLLATE NOCASE ASC, p.name COLLATE NOCASE ASC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortGroupAsc(String q, String groupFilter);

    // сортировка по группе (обратный порядок)
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY IFNULL(g.name,'') COLLATE NOCASE DESC, p.name COLLATE NOCASE ASC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortGroupDesc(String q, String groupFilter);

    // сортировка по контактам
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY IFNULL(p.contacts,'') COLLATE NOCASE ASC, p.name COLLATE NOCASE ASC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortContactsAsc(String q, String groupFilter);

    // сортировка по контактам (обратный порядок)
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
            "FROM persons p " +
            "LEFT JOIN `groups` g ON g.id = p.groupId " +
            "WHERE " +
            "(:groupFilter = 'ALL' OR (:groupFilter = 'NONE' AND p.groupId IS NULL) OR p.groupId = :groupFilter) " +
            "AND (:q IS NULL OR :q = '' OR " +
            "LOWER(p.name) LIKE :q OR LOWER(IFNULL(p.contacts,'')) LIKE :q OR LOWER(IFNULL(g.name,'')) LIKE :q) " +
            "ORDER BY IFNULL(p.contacts,'') COLLATE NOCASE DESC, p.name COLLATE NOCASE ASC")
    LiveData<List<PersonWithGroup>> observeFilteredByGroupSortContactsDesc(String q, String groupFilter);

    // проверка существования пользователя с такими контактами
    @Query("SELECT COUNT(*) FROM persons " +
            "WHERE contacts IS NOT NULL AND TRIM(contacts) != '' " +
            "AND LOWER(contacts) = LOWER(:contacts) " +
            "AND (:excludeId IS NULL OR id != :excludeId)")
    int existsByContacts(String contacts, String excludeId);

    // поиск id пользователя по контактам
    @Query("SELECT id FROM persons " +
            "WHERE contacts IS NOT NULL AND TRIM(contacts) != '' " +
            "AND LOWER(contacts) = LOWER(:contacts) LIMIT 1")
    String findIdByContacts(String contacts);
}
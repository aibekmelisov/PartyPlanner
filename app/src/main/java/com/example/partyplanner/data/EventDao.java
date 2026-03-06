package com.example.partyplanner.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface EventDao {

    // добавление или обновление списка событий
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAllEvents(List<EventEntity> events);

    // добавление или обновление одного события
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertEvent(EventEntity event);

    // добавление или обновление связей "событие-гость"
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCrossRefs(List<EventGuestCrossRef> refs);

    // удаление всех гостей конкретного события
    @Query("DELETE FROM event_guests WHERE eventId = :eventId")
    void deleteGuestsForEvent(String eventId);

    // удаление события по идентификатору
    @Query("DELETE FROM events WHERE id = :eventId")
    void deleteEventById(String eventId);

    // получение всех событий вместе с деталями
    @Transaction
    @Query("SELECT * FROM events ORDER BY dateTime ASC")
    LiveData<List<EventWithDetails>> observeAllEventsWithDetails();

    // получение будущих событий
    @Transaction
    @Query("SELECT * FROM events WHERE dateTime >= :now ORDER BY dateTime ASC")
    LiveData<List<EventWithDetails>> observeUpcoming(long now);

    // получение прошедших событий
    @Transaction
    @Query("SELECT * FROM events WHERE dateTime < :now ORDER BY dateTime DESC")
    LiveData<List<EventWithDetails>> observePast(long now);

    // события, где пользователь является организатором
    @Transaction
    @Query("SELECT * FROM events WHERE organizerId = :personId ORDER BY dateTime ASC")
    LiveData<List<EventWithDetails>> observeMineAsOrganizer(String personId);

    // события, куда пользователь приглашён как гость
    @Transaction
    @Query("SELECT e.* FROM events e INNER JOIN event_guests eg ON eg.eventId = e.id " +
            "WHERE eg.personId = :personId ORDER BY e.dateTime ASC")
    LiveData<List<EventWithDetails>> observeInvited(String personId);

    // получение одного события по id
    @Transaction
    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    LiveData<EventWithDetails> observeById(String id);

    // удаление события
    @Query("DELETE FROM events WHERE id = :id")
    void deleteById(String id);

    // удаление всех гостей события
    @Query("DELETE FROM event_guests WHERE eventId = :id")
    void deleteGuestsByEventId(String id);

    // получение списка всех людей
    @Query("SELECT * FROM persons ORDER BY name ASC")
    LiveData<List<PersonEntity>> observeAllPersons();

    // удаление конкретного гостя из события
    @Query("DELETE FROM event_guests WHERE eventId = :eventId AND personId = :personId")
    void removeGuest(String eventId, String personId);

    // добавление или обновление человека
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPerson(PersonEntity p);

    // удаление человека
    @Query("DELETE FROM persons WHERE id = :id")
    void deletePerson(String id);

    // удаление всех связей гостя с событиями
    @Query("DELETE FROM event_guests WHERE personId = :personId")
    void deleteGuestLinks(String personId);

    // подсчёт событий, где пользователь организатор
    @Query("SELECT COUNT(*) FROM events WHERE organizerId = :personId")
    int countOrganizerEvents(String personId);

    // подсчёт событий, где пользователь участвует как гость
    @Query("SELECT COUNT(*) FROM event_guests WHERE personId = :personId")
    int countGuestLinks(String personId);

    // добавление одного гостя в событие
    // если запись уже существует, вернётся -1
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long addGuest(EventGuestCrossRef ref);

    // добавление нескольких гостей
    // для дублей также возвращается -1
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long[] addGuests(List<EventGuestCrossRef> refs);

    // получение списка людей вместе с названиями их групп
    @Query(
            "SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
                    "FROM persons p " +
                    "LEFT JOIN `groups` g ON g.id = p.groupId " +
                    "ORDER BY p.name ASC"
    )
    LiveData<List<PersonWithGroup>> observeAllPersonsWithGroup();

    // получение одного человека вместе с группой
    @Query(
            "SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, g.name AS groupName " +
                    "FROM persons p " +
                    "LEFT JOIN `groups` g ON g.id = p.groupId " +
                    "WHERE p.id = :id LIMIT 1"
    )
    LiveData<PersonWithGroup> observePersonWithGroup(String id);

    // обновление RSVP-статуса гостя
    @Query("UPDATE event_guests SET status = :status WHERE eventId = :eventId AND personId = :personId")
    void updateGuestStatus(String eventId, String personId, String status);

    // подсчёт количества гостей по статусу
    @Query("SELECT COUNT(*) FROM event_guests WHERE eventId = :eventId AND status = :status")
    int countGuestsByStatus(String eventId, String status);

    // получение списка гостей события вместе с их статусами
    @Query("SELECT p.id, p.name, p.photoUrl, p.contacts, p.groupId, eg.status AS status " +
            "FROM persons p INNER JOIN event_guests eg ON eg.personId = p.id " +
            "WHERE eg.eventId = :eventId ORDER BY p.name ASC")
    LiveData<List<GuestWithStatus>> observeGuestsWithStatus(String eventId);

    // проверка: участвует ли человек в другом событии в то же время
    @Query(
            "SELECT COUNT(*) " +
                    "FROM event_guests eg " +
                    "INNER JOIN events e ON e.id = eg.eventId " +
                    "WHERE eg.personId = :personId " +
                    "AND e.dateTime = (SELECT dateTime FROM events WHERE id = :eventId) " +
                    "AND e.id != :eventId"
    )
    int countPersonBusyAtEventTime(String personId, String eventId);

    // получение списка занятых людей из выбранного набора
    @Query(
            "SELECT DISTINCT eg.personId " +
                    "FROM event_guests eg " +
                    "INNER JOIN events e ON e.id = eg.eventId " +
                    "WHERE eg.personId IN (:personIds) " +
                    "AND e.dateTime = (SELECT dateTime FROM events WHERE id = :eventId) " +
                    "AND e.id != :eventId"
    )
    List<String> findBusyPersonsAtEventTime(List<String> personIds, String eventId);

    // проверка занятости гостей при сохранении/редактировании события
    @Query(
            "SELECT DISTINCT eg.personId " +
                    "FROM event_guests eg " +
                    "INNER JOIN events e ON e.id = eg.eventId " +
                    "WHERE eg.personId IN (:personIds) " +
                    "AND e.dateTime = :dateTime " +
                    "AND e.id != :excludeEventId"
    )
    List<String> findBusyPersonsAtDateTime(List<String> personIds, long dateTime, String excludeEventId);

    // проверка занятости одного человека по времени
    // учитываются роли организатора и гостя
    @Query(
            "SELECT COUNT(*) FROM (" +
                    "  SELECT e.id FROM events e " +
                    "  WHERE e.dateTime = :dateTime AND e.organizerId = :personId AND e.id != :excludeEventId " +
                    "  UNION " +
                    "  SELECT e2.id FROM events e2 " +
                    "  INNER JOIN event_guests eg ON eg.eventId = e2.id " +
                    "  WHERE e2.dateTime = :dateTime AND eg.personId = :personId AND e2.id != :excludeEventId " +
                    ")"
    )
    int countPersonBusyAtDateTime(String personId, long dateTime, String excludeEventId);
}
package com.example.partyplanner.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
@Entity(tableName = "event_guests", primaryKeys = {"eventId", "personId"})
public class EventGuestCrossRef {

    // идентификатор события
    @NonNull public String eventId;

    // идентификатор пользователя (гостя)
    @NonNull public String personId;

    // статус участия пользователя в событии
    // возможные значения: INVITED / GOING / MAYBE / DECLINED
    @NonNull public String status;

    // основной конструктор, который используется Room
    public EventGuestCrossRef(@NonNull String eventId,
                              @NonNull String personId,
                              @NonNull String status) {

        this.eventId = eventId;
        this.personId = personId;

        // если статус не указан — устанавливается значение INVITED
        this.status = (status == null || status.trim().isEmpty())
                ? "INVITED"
                : status;
    }

    // дополнительный конструктор для удобства работы в коде
    // Room его игнорирует благодаря аннотации @Ignore
    @Ignore
    public EventGuestCrossRef(@NonNull String eventId,
                              @NonNull String personId) {

        this(eventId, personId, "INVITED");
    }
}
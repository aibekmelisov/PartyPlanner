package com.example.partyplanner.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "events",
        foreignKeys = @ForeignKey(
                entity = PersonEntity.class,
                parentColumns = "id",
                childColumns = "organizerId",
                onDelete = ForeignKey.SET_NULL
        ),
        indices = {@Index("organizerId")}
)
public class EventEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String title;
    public String posterUrl;
    public String address;
    public long dateTime;
    // EventEntity.java
    public String inviteTemplateId;   // "classic", "birthday", ...
    public String inviteMessage;      // кастомный текст (может быть null/пусто)
    public int inviteAccentColor;     // цвет акцента (ARGB int)
    public boolean inviteIncludeMap;  // ссылку на карту

    public String organizerId; // FK -> persons.id (может быть null)
    public boolean reminderEnabled;
    public long reminderAtMillis; // точное время напоминания
    public EventEntity() {}

    @Ignore
    public EventEntity(@NonNull String id,
                       String title,
                       String posterUrl,
                       String address,
                       long dateTime,
                       String organizerId) {

        this.id = id;
        this.title = title;
        this.posterUrl = posterUrl;
        this.address = address;
        this.dateTime = dateTime;
        this.organizerId = organizerId;

        // значения по умолчанию для приглашения
        this.inviteTemplateId = "classic";
        this.inviteMessage = "";
        this.inviteAccentColor = -1;
        this.inviteIncludeMap = true;
        this.reminderEnabled = false;
        this.reminderAtMillis = 0L;
    }
}

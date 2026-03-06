package com.example.partyplanner.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// описание таблицы "persons" в базе данных Room
@Entity(
        tableName = "persons",

        // связь с таблицей групп
        foreignKeys = @ForeignKey(
                entity = GroupEntity.class,   // родительская таблица
                parentColumns = "id",         // поле в таблице групп
                childColumns = "groupId",     // поле в текущей таблице
                onDelete = ForeignKey.SET_NULL // при удалении группы поле обнуляется
        ),

        // индекс для ускорения поиска по groupId
        indices = {@Index("groupId")}
)
public class PersonEntity {

    // уникальный идентификатор пользователя
    @PrimaryKey
    @NonNull
    public String id;

    // имя пользователя
    public String name;

    // ссылка на фотографию пользователя
    public String photoUrl;

    // контактные данные (телефон, email и т.п.)
    public String contacts;

    // идентификатор группы (может быть null)
    public String groupId;

    // конструктор сущности
    public PersonEntity(@NonNull String id,
                        String name,
                        String photoUrl,
                        String contacts,
                        String groupId) {

        this.id = id;
        this.name = name;
        this.photoUrl = photoUrl;
        this.contacts = contacts;
        this.groupId = groupId;
    }
}
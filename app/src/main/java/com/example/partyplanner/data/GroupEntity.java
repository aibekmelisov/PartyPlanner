package com.example.partyplanner.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "groups")
public class GroupEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String name;

    public GroupEntity(@NonNull String id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }
}
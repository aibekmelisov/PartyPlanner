package com.example.partyplanner.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(GroupEntity g);

    @Query("SELECT * FROM groups ORDER BY name")
    LiveData<List<GroupEntity>> observeAll();

    @Query("SELECT id FROM groups WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    String findIdByNameSync(String name);
}
package com.example.partyplanner.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {PersonEntity.class, GroupEntity.class, EventEntity.class, EventGuestCrossRef.class},
        version = 8,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PersonDao personDao();
    public abstract GroupDao groupDao();
    public abstract EventDao eventDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `groups` (id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(id))");

            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS persons_new (" +
                            "id TEXT NOT NULL, " +
                            "name TEXT, " +
                            "photoUrl TEXT, " +
                            "contacts TEXT, " +
                            "groupId TEXT, " +
                            "PRIMARY KEY(id), " +
                            "FOREIGN KEY(groupId) REFERENCES `groups`(id) ON DELETE SET NULL" +
                            ")"
            );

            db.execSQL(
                    "INSERT INTO persons_new (id, name, photoUrl, contacts, groupId) " +
                            "SELECT id, name, photoUrl, contacts, NULL FROM persons"
            );

            db.execSQL("DROP TABLE persons");
            db.execSQL("ALTER TABLE persons_new RENAME TO persons");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_persons_groupId ON persons(groupId)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS persons_new (" +
                            "id TEXT NOT NULL, " +
                            "name TEXT, " +
                            "photoUrl TEXT, " +
                            "contacts TEXT, " +
                            "groupId TEXT, " +
                            "PRIMARY KEY(id), " +
                            "FOREIGN KEY(groupId) REFERENCES `groups`(id) ON DELETE SET NULL" +
                            ")"
            );

            db.execSQL(
                    "INSERT INTO persons_new (id, name, photoUrl, contacts, groupId) " +
                            "SELECT id, name, photoUrl, contacts, groupId FROM persons"
            );

            db.execSQL("DROP TABLE persons");
            db.execSQL("ALTER TABLE persons_new RENAME TO persons");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_persons_groupId ON persons(groupId)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            // пусто: закрываем цепочку миграций
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE events ADD COLUMN inviteTemplateId TEXT");
            db.execSQL("ALTER TABLE events ADD COLUMN inviteMessage TEXT");
            db.execSQL("ALTER TABLE events ADD COLUMN inviteAccentColor INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE events ADD COLUMN inviteIncludeMap INTEGER NOT NULL DEFAULT 1");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE event_guests ADD COLUMN status TEXT NOT NULL DEFAULT 'INVITED'");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE events ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE events ADD COLUMN reminderAtMillis INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE events ADD COLUMN reminderMinutes INTEGER NOT NULL DEFAULT 30");
        }
    };
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context, AppDatabase.class, "party.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
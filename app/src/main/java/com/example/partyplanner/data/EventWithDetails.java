package com.example.partyplanner.data;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class EventWithDetails {
    @Embedded
    public EventEntity event;

    @Relation(
            parentColumn = "organizerId",
            entityColumn = "id"
    )
    public PersonEntity organizer;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = EventGuestCrossRef.class,
                    parentColumn = "eventId",
                    entityColumn = "personId"
            )
    )
    public List<PersonEntity> guests;
}

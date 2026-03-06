package com.example.partyplanner.data.api;

import java.util.List;

public class EventDto {
    public String id;
    public String title;
    public String posterUrl;
    public String address;
    public long dateTime;

    public PersonDto organizer;
    public List<PersonDto> guests;
}

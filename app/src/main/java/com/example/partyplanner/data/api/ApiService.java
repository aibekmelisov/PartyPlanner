package com.example.partyplanner.data.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("events")
    Call<List<EventDto>> getEvents();
}

package com.example.partyplanner.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.partyplanner.data.EventEntity;
import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.PartyRepository;

public class EventDetailsViewModel extends AndroidViewModel {

    private final PartyRepository repo;

    //mock base url (с / на конце)
    private static final String BASE_URL =
            "https://c5b41663-48d3-4135-8955-24fca9bfe475.mock.pstmn.io/";

    public EventDetailsViewModel(@NonNull Application app) {
        super(app);
        repo = new PartyRepository(app, BASE_URL);
    }

    public void save(EventEntity event) {
        repo.saveEvent(event);
    }

    public LiveData<EventWithDetails> getEvent(String id) {
        return repo.observeEvent(id);
    }

    public void delete(String id) {
        repo.deleteEvent(id);
    }

    public void createInvite(EventWithDetails e, String personId,
                             java.util.function.Consumer<String> onDone) {
        repo.createInviteForGuest(e, personId, onDone);
    }
}

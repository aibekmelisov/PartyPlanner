package com.example.partyplanner.ui;

import android.app.Application;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.partyplanner.data.EventEntity;
import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonWithGroup;

import java.util.List;

public class EditEventViewModel extends AndroidViewModel {

    private final PartyRepository repo;

        private static final String BASE_URL =
            "https://c5b41663-48d3-4135-8955-24fca9bfe475.mock.pstmn.io/";

    public EditEventViewModel(@NonNull Application app) {
        super(app);
        repo = new PartyRepository(app, BASE_URL);
    }

    public LiveData<EventWithDetails> getEvent(String id) {
        return repo.observeEvent(id);
    }

    public void save(EventEntity e) {
        repo.saveEvent(e);
    }

    //тут уже groupName
    public LiveData<List<PersonWithGroup>> getAllPersonsWithGroup() {
        return repo.observeAllPersonsWithGroup();
    }

    public LiveData<List<GroupEntity>> getGroups() {
        return repo.observeAllGroups();
    }

    public void replaceGuests(String eventId, List<String> guestIds) {
        repo.replaceGuests(eventId, guestIds);
    }

    public void checkGuestsBusyAtDateTime(String excludeEventId, long dateTime, List<String> guestIds,
                                          java.util.function.Consumer<List<String>> onDone) {
        repo.checkGuestsBusyAtDateTime(excludeEventId, dateTime, guestIds, onDone);
    }
    public void isPersonBusyAtDateTime(String personId, long dateTime, String excludeEventId,
                                       java.util.function.Consumer<Boolean> onDone) {
        repo.isPersonBusyAtDateTime(personId, dateTime, excludeEventId, onDone);
    }

    public void saveEventWithGuests(EventEntity e, List<String> guestIds, Runnable onDone) {
        repo.saveEventWithGuests(e, guestIds, onDone);
    }
}
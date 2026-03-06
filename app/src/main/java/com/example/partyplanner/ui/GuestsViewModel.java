package com.example.partyplanner.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.PartyRepository;
import com.example.partyplanner.data.PersonEntity;

import java.util.List;
import com.example.partyplanner.data.GroupEntity;
import java.util.ArrayList;
import com.google.firebase.firestore.ListenerRegistration;

public class GuestsViewModel extends AndroidViewModel {

    private final PartyRepository repo;

    private ListenerRegistration rsvpListener;
    private static final String BASE_URL =
            "https://c5b41663-48d3-4135-8955-24fca9bfe475.mock.pstmn.io/";

    public GuestsViewModel(@NonNull Application app) {
        super(app);
        repo = new PartyRepository(app, BASE_URL);
    }

    public LiveData<EventWithDetails> getEvent(String eventId) {
        return repo.observeEvent(eventId);
    }

    public LiveData<List<PersonEntity>> getAllPersons() {
        return repo.observeAllPersons();
    }

    public void addGuest(String eventId, String personId) {
        repo.addGuest(eventId, personId);
    }

    public void addGuestChecked(String eventId, String personId, java.util.function.Consumer<Boolean> onDone) {
        repo.addGuestChecked(eventId, personId, onDone);
    }
    public void removeGuest(String eventId, String personId) {
        repo.removeGuest(eventId, personId);
    }

    public void addGuestsBulkChecked(String eventId, java.util.List<String> personIds,
                                     java.util.function.Consumer<PartyRepository.AddGuestsReport> onDone) {
        repo.addGuestsBulkChecked(eventId, personIds, onDone);
    }

    public LiveData<List<GroupEntity>> getGroups() {
        return repo.observeAllGroups();
    }

    public void addGuestsBulk(String eventId, List<String> personIds) {
        repo.addGuestsBulk(eventId, personIds);
    }

    public void updateStatus(String eventId, String personId, String status) {
        repo.updateGuestStatus(eventId, personId, status);
    }
    public LiveData<List<com.example.partyplanner.data.GuestWithStatus>> getGuests(String eventId) {
        return repo.observeGuestsWithStatus(eventId);
    }

    public void startRealtime(String eventId) {
        if (rsvpListener != null) rsvpListener.remove();
        rsvpListener = repo.observeRsvpRealtime(eventId);
    }

    @Override
    protected void onCleared() {
        if (rsvpListener != null) rsvpListener.remove();
        super.onCleared();
    }

    public void addGuestCheckedWithTimeConflict(String eventId, String personId,
                                                java.util.function.Consumer<Integer> onDone) {
        repo.addGuestCheckedWithTimeConflict(eventId, personId, onDone);
    }

    public void addGuestsBulkCheckedWithTimeConflict(String eventId, List<String> personIds,
                                                     java.util.function.Consumer<PartyRepository.AddGuestsReport2> onDone) {
        repo.addGuestsBulkCheckedWithTimeConflict(eventId, personIds, onDone);
    }

    public void getRsvpStats(String eventId, java.util.function.Consumer<com.example.partyplanner.data.RsvpStats> onDone) {
        repo.getRsvpStats(eventId, onDone);
    }



}

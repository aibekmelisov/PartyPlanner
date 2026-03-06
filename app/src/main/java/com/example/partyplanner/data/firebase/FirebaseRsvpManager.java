package com.example.partyplanner.data.firebase;

import com.example.partyplanner.data.EventWithDetails;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.FieldValue;
import java.util.function.Consumer;

public class FirebaseRsvpManager {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void updateStatus(String eventId, String personId, String status) {
        DocumentReference ref = db.collection("events")
                .document(eventId)
                .collection("guests")
                .document(personId);

        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("updatedAt", System.currentTimeMillis());

        ref.set(data);
    }

    public ListenerRegistration listenForGuestUpdates(
            String eventId,
            RsvpListener listener
    ) {
        return db.collection("events")
                .document(eventId)
                .collection("guests")
                .addSnapshotListener((snap, e) -> {

                    if (snap == null) return;

                    snap.getDocuments().forEach(doc -> {
                        String personId = doc.getId();
                        String status = doc.getString("status");
                        listener.onStatusChanged(personId, status);
                    });
                });
    }

    public interface RsvpListener {
        void onStatusChanged(String personId, String status);
    }



    public void createInvite(EventWithDetails e, String personId, java.util.function.Consumer<String> onDone) {

        DocumentReference ref = db.collection("invites").document();

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", e.event.id);
        data.put("personId", personId);
        data.put("createdAt", System.currentTimeMillis());

        // snapshot данных события (для веб-страницы)
        data.put("eventTitle", e.event.title);
        data.put("eventDateTime", e.event.dateTime);
        data.put("eventAddress", e.event.address);

        if (e.organizer != null)
            data.put("organizerName", e.organizer.name);
        else
            data.put("organizerName", "—");

        ref.set(data)
                .addOnSuccessListener(v -> onDone.accept(ref.getId()))
                .addOnFailureListener(e2 -> onDone.accept(null));
    }
}
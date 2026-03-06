package com.example.partyplanner.ui.invite;

import android.net.Uri;
import android.text.TextUtils;

import com.example.partyplanner.data.EventWithDetails;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class InviteComposer {

    private InviteComposer() {}

    public static String buildSubject(EventWithDetails e) {
        return "Invitation: " + safe(e.event.title, "Event");
    }

    public static String buildText(EventWithDetails e) {
        String title = safe(e.event.title, "Event");
        String organizer = (e.organizer != null && !TextUtils.isEmpty(e.organizer.name)) ? e.organizer.name : "—";
        String date = DateFormat.getDateTimeInstance().format(new Date(e.event.dateTime));
        String address = safe(e.event.address, "—");

        StringBuilder sb = new StringBuilder();

        // кастомный текст, если есть
        if (!TextUtils.isEmpty(e.event.inviteMessage)) {
            sb.append(e.event.inviteMessage.trim()).append("\n\n");
        } else {
            sb.append("You are invited!").append("\n\n");
        }

        sb.append("Event: ").append(title).append("\n");
        sb.append("Date: ").append(date).append("\n");
        sb.append("Address: ").append(address).append("\n");
        sb.append("Organizer: ").append(organizer).append("\n");

        if (e.guests != null) {
            sb.append("Guests: ").append(e.guests.size()).append("\n");
        }

        if (e.event.inviteIncludeMap) {
            sb.append("\nMap: ").append(buildMapsUrl(address)).append("\n");
        }

        sb.append("\nSee you there!");
        return sb.toString();
    }

    public static String buildMapsUrl(String address) {
        if (TextUtils.isEmpty(address)) return "";
        String q = Uri.encode(address);
        return "https://www.google.com/maps/search/?api=1&query=" + q;
    }


    private static String safe(String s, String def) {
        return TextUtils.isEmpty(s) ? def : s;
    }

    public static String buildRsvpLink(String eventId, String personId) {
        return "https://party-1f18b.web.app/?eventId="
                + Uri.encode(eventId)
                + "&personId="
                + Uri.encode(personId);
    }

    public static String buildTextForRecipient(EventWithDetails e, String personId) {
        String base = buildText(e);
        String rsvp = buildRsvpLink(e.event.id, personId);
        return base
                + "\n\nRSVP (respond):\n" + rsvp
                + "\n\nChoose in app: GOING / MAYBE / DECLINED";
    }





    public static String buildWebInviteLink(String inviteId) {
        return "https://party-1f18b.web.app/?invite=" + Uri.encode(inviteId);
    }

    public static String buildTextForRecipientWithInviteId(EventWithDetails e, String inviteId) {
        String base = buildText(e);
        String rsvp = buildWebInviteLink(inviteId);
        return base
                + "\n\nRSVP (respond):\n" + rsvp
                + "\n\nChoose: GOING / MAYBE / DECLINED";
    }
}
package com.example.partyplanner.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.data.GuestWithStatus;

import java.util.ArrayList;
import java.util.List;

public class GuestsAdapter extends RecyclerView.Adapter<GuestsAdapter.VH> {

    public interface Listener {
        void onClick(GuestWithStatus g);
        void onLongClick(GuestWithStatus g);

        void onStatusChange(GuestWithStatus g, String newStatus);
    }

    private final List<GuestWithStatus> items = new ArrayList<>();
    private final Listener listener;

    public GuestsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitGuests(List<GuestWithStatus> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guest_rsvp, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GuestWithStatus g = items.get(position);

        h.tvName.setText(g.name);
        h.tvContacts.setText(g.contacts != null ? g.contacts : "—");

        String status = (g.status == null || g.status.trim().isEmpty())
                ? "INVITED" : g.status.trim();

        h.tvStatus.setText("Status: " + status);

        //сначала сбрасываем всё
        h.btnGoing.setOnClickListener(v -> {
            g.status = "GOING";
            notifyItemChanged(h.getAdapterPosition());
            listener.onStatusChange(g, "GOING");
        });

        h.btnMaybe.setOnClickListener(v -> {
            g.status = "MAYBE";
            notifyItemChanged(h.getAdapterPosition());
            listener.onStatusChange(g, "MAYBE");
        });

        h.btnDeclined.setOnClickListener(v -> {
            g.status = "DECLINED";
            notifyItemChanged(h.getAdapterPosition());
            listener.onStatusChange(g, "DECLINED");
        });
        //потом подсвечиваем нужную
        int cDefault = ContextCompat.getColor(h.itemView.getContext(), R.color.rsvp_default);
        int cGreen   = ContextCompat.getColor(h.itemView.getContext(), R.color.rsvp_green);
        int cYellow  = ContextCompat.getColor(h.itemView.getContext(), R.color.rsvp_yellow);
        int cRed     = ContextCompat.getColor(h.itemView.getContext(), R.color.rsvp_red);

// reset
        h.btnGoing.setBackgroundTintList(ColorStateList.valueOf(cDefault));
        h.btnMaybe.setBackgroundTintList(ColorStateList.valueOf(cDefault));
        h.btnDeclined.setBackgroundTintList(ColorStateList.valueOf(cDefault));

        switch (status) {
            case "GOING":
                h.btnGoing.setBackgroundTintList(ColorStateList.valueOf(cGreen));
                break;
            case "MAYBE":
                h.btnMaybe.setBackgroundTintList(ColorStateList.valueOf(cYellow));
                break;
            case "DECLINED":
                h.btnDeclined.setBackgroundTintList(ColorStateList.valueOf(cRed));
                break;
        }

        h.btnGoing.setOnClickListener(v ->
                listener.onStatusChange(g, "GOING"));

        h.btnMaybe.setOnClickListener(v ->
                listener.onStatusChange(g, "MAYBE"));

        h.btnDeclined.setOnClickListener(v ->
                listener.onStatusChange(g, "DECLINED"));

        h.itemView.setOnClickListener(v -> listener.onClick(g));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(g);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvContacts, tvStatus;
        MaterialButton btnGoing, btnMaybe, btnDeclined;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvContacts = itemView.findViewById(R.id.tvContacts);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnGoing = itemView.findViewById(R.id.btnGoing);
            btnMaybe = itemView.findViewById(R.id.btnMaybe);
            btnDeclined = itemView.findViewById(R.id.btnDeclined);
        }
    }
}
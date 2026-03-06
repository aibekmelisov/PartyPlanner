package com.example.partyplanner.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.data.PersonWithGroup;

import java.util.ArrayList;
import java.util.List;

public class PersonsAdapter extends RecyclerView.Adapter<PersonsAdapter.VH> {

    // интерфейс обработки кликов по элементу списка
    public interface OnPersonClickListener {
        void onClick(PersonWithGroup p);
        void onLongClick(PersonWithGroup p);
    }

    // список людей для отображения
    private final List<PersonWithGroup> items = new ArrayList<>();

    // обработчик кликов
    private final OnPersonClickListener listener;

    public PersonsAdapter(OnPersonClickListener listener) {
        this.listener = listener;
    }

    // обновление списка людей
    public void submit(List<PersonWithGroup> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    // создание элемента списка
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_person, parent, false);

        return new VH(v);
    }

    // привязка данных к элементу списка
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        PersonWithGroup p = items.get(position);

        h.tvName.setText(p.name);
        h.tvContacts.setText(p.contacts != null ? p.contacts : "—");

        // отображение группы пользователя
        String g = (p.groupName != null && !p.groupName.trim().isEmpty())
                ? p.groupName
                : "Без группы";

        h.tvGroup.setText(g);

        // обработка кликов
        h.itemView.setOnClickListener(v -> listener.onClick(p));

        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder элемента списка
    static class VH extends RecyclerView.ViewHolder {

        TextView tvName, tvContacts, tvGroup;

        VH(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tvName);
            tvContacts = itemView.findViewById(R.id.tvContacts);
            tvGroup = itemView.findViewById(R.id.tvGroup);
        }
    }
}
package com.example.partyplanner.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.partyplanner.R;
import com.example.partyplanner.data.EventWithDetails;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PersonEntity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class EventsAdapter extends RecyclerView.Adapter<EventsAdapter.VH> {

    // Интерфейс для обработки нажатия на карточку события
    public interface OnEventClickListener {
        void onClick(EventWithDetails event);
    }

    // Список событий для отображения
    private final List<EventWithDetails> items = new ArrayList<>();

    // Обработчик клика по элементу
    private final OnEventClickListener listener;

    // Карта групп: groupId -> groupName
    // Нужна для отображения названий групп гостей
    private final HashMap<String, String> groupMap = new HashMap<>();

    public EventsAdapter(OnEventClickListener listener) {
        this.listener = listener;
    }

    // Обновление списка событий
    public void submit(List<EventWithDetails> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged(); // перерисовываем список
    }

    // Передача списка групп в адаптер
    public void setGroups(List<GroupEntity> groups) {
        groupMap.clear();

        if (groups != null) {
            for (GroupEntity g : groups) {
                if (g != null && g.id != null)
                    groupMap.put(g.id, g.name);
            }
        }

        notifyDataSetChanged(); // обновляем отображение групп
    }

    // Создание ViewHolder для карточки события
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);

        return new VH(v);
    }

    // Привязка данных события к карточке
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EventWithDetails e = items.get(position);

        // Название события
        h.title.setText(safe(e.event.title));

        // Форматированная дата и время
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(e.event.dateTime));
        h.date.setText(date);

        // Адрес события
        h.address.setText(safe(e.event.address));

        // Имя организатора
        h.organizer.setText(e.organizer != null ? safe(e.organizer.name) : "—");

        // Количество гостей
        int guests = (e.guests != null) ? e.guests.size() : 0;
        h.guestsCount.setText(formatGuests(guests));

        // Краткая сводка по группам гостей
        h.guestsByGroups.setText(buildGroupsSummary(e));

        // Нажатие на карточку события
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(e);
        });
    }

    // Формирует строку вида:
    // "Друзья: 2 • Коллеги: 3 • Без группы: 1"
    private String buildGroupsSummary(EventWithDetails e) {
        if (e == null || e.guests == null || e.guests.isEmpty()) return "—";

        HashMap<String, Integer> counts = new HashMap<>();
        int noGroup = 0;

        // Подсчитываем количество гостей по группам
        for (PersonEntity p : e.guests) {
            if (p == null) continue;

            String gid = p.groupId;

            if (gid == null || gid.trim().isEmpty()) {
                noGroup++;
            } else {
                counts.put(gid, (counts.get(gid) == null ? 0 : counts.get(gid)) + 1);
            }
        }

        StringBuilder sb = new StringBuilder();

        // Сначала добавляем обычные группы
        for (String gid : counts.keySet()) {
            String gname = groupMap.get(gid);

            if (gname == null || gname.trim().isEmpty())
                gname = "Группа";

            sb.append(gname)
                    .append(": ")
                    .append(counts.get(gid))
                    .append(" • ");
        }

        // Затем добавляем гостей без группы
        if (noGroup > 0) {
            sb.append("Без группы: ")
                    .append(noGroup)
                    .append(" • ");
        }

        // Если строка пустая — выводим прочерк
        if (sb.length() == 0) return "—";

        // Убираем последний разделитель " • "
        return sb.substring(0, sb.length() - 3);
    }

    // Безопасный вывод строки
    // Если строка пустая или null — возвращаем "—"
    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
    }

    // Форматирование количества гостей
    private String formatGuests(int n) {
        if (n == 1) return "1 guest";
        return n + " guests";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder хранит ссылки на элементы карточки
    static class VH extends RecyclerView.ViewHolder {
        TextView title, date, address, organizer, guestsCount, guestsByGroups;

        VH(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.tvTitle);
            date = itemView.findViewById(R.id.tvDate);
            address = itemView.findViewById(R.id.tvAddress);
            organizer = itemView.findViewById(R.id.tvOrganizer);
            guestsCount = itemView.findViewById(R.id.tvGuestsCount);
            guestsByGroups = itemView.findViewById(R.id.tvGuestsByGroups);
        }
    }
}
package com.example.partyplanner.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.partyplanner.R;
import com.example.partyplanner.data.GroupEntity;
import com.example.partyplanner.data.PersonEntity;

import java.util.UUID;

public class EditPersonActivity extends AppCompatActivity {

    private String loadedGroupId = null;
    private java.util.List<GroupEntity> groupsCache = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_edit_person);

        TextView tvHeader = findViewById(R.id.tvHeader);

        EditText etName = findViewById(R.id.etName);
        EditText etContacts = findViewById(R.id.etContacts);
        EditText etPhoto = findViewById(R.id.etPhoto);
        AutoCompleteTextView etGroup = findViewById(R.id.etGroup);

        Button btnSave = findViewById(R.id.btnSavePerson);

        String personId = getIntent().getStringExtra("personId");
        tvHeader.setText(personId == null ? "Новый человек" : "Редактирование");

        PersonsViewModel vm = new ViewModelProvider(this).get(PersonsViewModel.class);

        // 1) список групп для dropdown
        vm.getGroups().observe(this, groups -> {
            groupsCache = (groups != null) ? groups : new java.util.ArrayList<>();

            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            names.add("Без группы");
            for (GroupEntity g : groupsCache) names.add(g.name);

            android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(
                    this, android.R.layout.simple_dropdown_item_1line, names
            );
            etGroup.setAdapter(a);

            // если человек уже загружен и у него есть groupId -> ставим имя
            applyGroupNameToField(etGroup);
        });

        // 2) загрузка человека
        if (personId != null) {
            vm.getOneWithGroup(personId).observe(this, p -> {
                if (p == null) return;

                etName.setText(p.name);
                etContacts.setText(p.contacts);
                etPhoto.setText(p.photoUrl);

                if (p.groupName != null) etGroup.setText(p.groupName, false);
                else etGroup.setText("Без группы", false);
            });
        } else {
            etGroup.setText("Без группы", false);
        }

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                etName.setError("Введите имя");
                return;
            }

            String groupName = etGroup.getText().toString().trim();
            if ("Без группы".equalsIgnoreCase(groupName) || groupName.isEmpty()) groupName = null;

            PersonEntity p = new PersonEntity(
                    personId != null ? personId : "p_" + UUID.randomUUID(),
                    name,
                    etPhoto.getText().toString(),
                    etContacts.getText().toString(),
                    null // groupId назначим в репозитории по groupName
            );

            vm.savePersonWithGroupName(p, groupName, success -> {
                if (success) {
                    finish();
                } else {
                    etContacts.setError("Такой контакт уже существует");
                }
            });
        });
    }

    private void applyGroupNameToField(AutoCompleteTextView etGroup) {
        if (loadedGroupId == null) return;
        for (GroupEntity g : groupsCache) {
            if (g != null && loadedGroupId.equals(g.id)) {
                etGroup.setText(g.name, false);
                return;
            }
        }
        // если groupId есть, но группу не нашли (удалили, сломали импорт и т.д.)
        etGroup.setText("Без группы", false);
    }
}
package com.wigdis.player;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class EmotionSettingsActivity extends AppCompatActivity {

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_emotion_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Spinner spinner = (Spinner) findViewById(R.id.spinner2);
        ArrayList<String> spinnerKeys = new ArrayList<>(PlayerActivity.emotionMap.keySet());
        initializeSpinner();

    }

    private void initializeSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.spinner2);
        ArrayList<String> spinnerKeys = new ArrayList<>(PlayerActivity.emotionMap.keySet());
        Collections.sort(spinnerKeys, String::compareTo);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinnerKeys);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        StorageUtil storageUtil = new StorageUtil(getApplicationContext());
        Mood mood = storageUtil.loadMood();
        int position = adapter.getPosition(mood.getName());
        spinner.setSelection(position, false);


        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {
                StorageUtil storageUtil = new StorageUtil(getApplicationContext());

                for (Map.Entry entry: PlayerActivity.emotionMap.entrySet()){
                    if(adapterView.getItemAtPosition(pos).toString().equals(entry.getKey().toString())){
                        Pair<Double,Double> pair = (Pair<Double, Double>) entry.getValue();

                        Mood mood = new Mood(entry.getKey().toString(), pair.first, pair.second);
                        storageUtil.storeMood(mood);
                        break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        spinner.getSelectedItem();
    }
}

package com.wigdis.player;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private MediaPlayerService player;
    boolean serviceBound = false;

    ArrayList<Audio> audioList;

    public static Map<String, Pair<Double, Double>> emotionMap = new HashMap<>();

    private final static int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    public static final String Broadcast_PLAY_NEW_AUDIO = "com.wigdis.player.PlayNewAudio";
    public static final String Broadcast_PLAY_ANOTHER = "com.wigdis.player.PlayAnother";


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            serviceBound = true;

            //todo remove later
            Toast.makeText(PlayerActivity.this, "Service Bound", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    class MyResultReceiver extends ResultReceiver
    {
        public MyResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if(resultCode == 200) {
                setTrackInfoOnView();
            } else if(resultCode == 1){
                ImageButton imageButton = findViewById(R.id.play_button);
                imageButton.setImageResource(android.R.drawable.ic_media_pause);
            } else if(resultCode == 0){
                ImageButton imageButton = findViewById(R.id.play_button);
                imageButton.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void setTrackInfoOnView() {
        TextView title = findViewById(R.id.title);
        TextView artist = findViewById(R.id.artist);

        title.setText(MediaPlayerService.activeAudio.getTitle());
        artist.setText(MediaPlayerService.activeAudio.getArtist());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeEmotionMap();

        setContentView(R.layout.activity_player);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setWelcome();

        //todo probably need to be extracted to another method
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }

        loadAudio();

        ImageButton playButton = (ImageButton) findViewById(R.id.play_button);
        ImageButton nextButton = (ImageButton) findViewById(R.id.next_button);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playAudio();

            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serviceBound) {
                    Intent broadcastIntent = new Intent(Broadcast_PLAY_ANOTHER);
                    sendBroadcast(broadcastIntent);
                } else {
                    playAudio();
                }
            }
        });
    }

    private void playAudio() {
        //Check is service is active
        if (!serviceBound) {
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);


            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            playerIntent.putExtra("receiver", new MyResultReceiver(null));
            startService(playerIntent);
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            openEmotionSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("ServiceState", serviceBound);
        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("ServiceState");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            player.stopSelf();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    setNoPermissionOnView();
                }
            }
        }
    }

    private void setNoPermissionOnView() {
        TextView title = findViewById(R.id.title);
        TextView artist = findViewById(R.id.artist);

        title.setText(R.string.NO_PERMISSION);
        artist.setText(R.string.NO_PERMISSION);
    }

    private void setWelcome() {
        TextView title = findViewById(R.id.title);
        TextView artist = findViewById(R.id.artist);

        title.setText(R.string.WELCOME);
        artist.setText("");
    }


    private void loadAudio() {

        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.DISPLAY_NAME+ " ASC";
        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);

        if (cursor != null && cursor.getCount() > 0) {
            audioList = new ArrayList<>();
            while (cursor.moveToNext()) {

                String displayname = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                Integer id = Integer.parseInt(displayname.substring(0, displayname.length() - 4));

                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));

                audioList.add(new Audio(data, title, album, artist, id));
            }
        }
        cursor.close();
    }

    private void openEmotionSettings() {
        Intent intent = new Intent(this, EmotionSettingsActivity.class);
        intent.putExtra("key", "value");
        startActivity(intent);
    }

    //set values - according to paper
    private void initializeEmotionMap() {

        emotionMap.put("Afraid", new Pair<>(-3.5, 7.));
        emotionMap.put("Alarmed", new Pair<>(-0.4, 7.9));
        emotionMap.put("Angry", new Pair<>(-1.1, 7.));
        emotionMap.put("Annoyed", new Pair<>(-3.8, 5.7));
        emotionMap.put("Aroused", new Pair<>(2.3, 8.1));
        emotionMap.put("Astonished", new Pair<>(3., 7.7));
        emotionMap.put("At ease", new Pair<>(6.5, -5.5));
        emotionMap.put("Bored", new Pair<>(-3.3, -6.4));
        emotionMap.put("Calm", new Pair<>(5.8, -6.3));
        emotionMap.put("Content", new Pair<>(6.8, -5.3));
        emotionMap.put("Delighted", new Pair<>(6.9, 3.2));
        emotionMap.put("Depressed", new Pair<>(-6.4, -4.1));
        emotionMap.put("Distressed", new Pair<>(-5.8, 5.));
        emotionMap.put("Droopy", new Pair<>(-2., -8.2));
        emotionMap.put("Excited", new Pair<>(5.5, 6.5));
        emotionMap.put("Frustrated", new Pair<>(-4.8, 4.));
        emotionMap.put("Glad", new Pair<>(8., -1.5));
        emotionMap.put("Gloomy", new Pair<>(-6.9, -4.2));
        emotionMap.put("Happy", new Pair<>(7.4, 1.));
        emotionMap.put("Miserable", new Pair<>(-8., -1.3));
        emotionMap.put("Pleased", new Pair<>(7.5, -1.));
        emotionMap.put("Relaxed", new Pair<>(6.1, -5.8));
        emotionMap.put("Sad", new Pair<>(-6.5, -3.5));
        emotionMap.put("Satisfied", new Pair<>(6.5, -5.8));
        emotionMap.put("Serene", new Pair<>(6.8, -4.4));
        emotionMap.put("Sleepy", new Pair<>(0.2, -9.));
        emotionMap.put("Tense", new Pair<>(-0.2, 7.3));
        emotionMap.put("Tired", new Pair<>(-0.3, -8.8));
    }
}

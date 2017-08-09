package com.gf169.reversehttptunnel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {
    private static String TAG = "gfMainActivity";

    boolean tunnelIsWorking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate called");//another activity is currently running (or user has pressed Home)
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Button buttonRun =
                (Button)findViewById(R.id.buttonRun);
        buttonRun.setOnClickListener(buttonRunListener);
        Button buttonConfig =
                (Button)findViewById(R.id.buttonConfig);
        buttonConfig.setOnClickListener(buttonConfigListener);

        if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) { // Google велит
            System.setProperty("http.keepAlive", "false");
        }
        if (PreferenceManager.getDefaultSharedPreferences(MainActivity.this).
                getAll().toString()=="{}") { // Самый первый раз - пусто
            SettingsActivity.iniPrefs(this); // set defaults
        }
        if (savedInstanceState == null) { // Запуск программы - сразу стартуем сервис
/*
            tunnelIsWorking=false;
            Intent intent = new Intent(MainActivity.this, TunnelService.class);
            intent.putExtra("Action", "Toggle state");
            startService(intent);
*/
            toggleServiceState();
        } else {
            tunnelIsWorking=savedInstanceState.getBoolean("tunnelIsWorking");
            updateButtonRun(savedInstanceState);
        }
        registerReceiver(broadcastReceiver, new IntentFilter("gfMessageFromTunnelService"));
    }
    void toggleServiceState() {
        Intent intent = new Intent(MainActivity.this, TunnelService.class);
        intent.putExtra("Action", "Toggle state");
        startService(intent);

        Button buttonRun =
                (Button)findViewById(R.id.buttonRun);
        buttonRun.setTextColor(Color.rgb(128,128,128));
        buttonRun.setText(tunnelIsWorking ? "Stopping..." : "Starting...");
        buttonRun.setClickable(false);
    }
    private OnClickListener buttonRunListener = new
            OnClickListener() {
                public void onClick(View v) {
                    // Запрет менять ориентацию пока не пришел ответ,
                    // чтобы он не пришел между onDestroy и onCreate и не пропал
                    // Можно было вместо этого воспользоваться sticky intent
                    if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

                    toggleServiceState();
                }
            };
    private OnClickListener buttonConfigListener = new
            OnClickListener() {
                public void onClick(View v) {
                    Intent intent=new Intent(MainActivity.this, SettingsActivity.class);
                    MainActivity.this.startActivity(intent);
                }
            };
    void updateButtonRun(Bundle savedInstanceState) {
        Button buttonRun =
                (Button)findViewById(R.id.buttonRun);
        if (savedInstanceState==null) {
            buttonRun.setTextColor(
                    tunnelIsWorking ? Color.RED : Color.GREEN);
            buttonRun.setText(
                    tunnelIsWorking ? "Stop" : "Run");
            buttonRun.setClickable(true);
        } else {
            buttonRun.setTextColor(savedInstanceState.getInt("buttonRunTextColor"));
            buttonRun.setText(savedInstanceState.getString("buttonRunText"));
            buttonRun.setClickable(savedInstanceState.getBoolean("buttonRunClickable"));
        }
    }
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            Log.d(TAG, "Got a message - " + action);

            if (action.equals("report service state")) {
                tunnelIsWorking = intent.getBooleanExtra("isWorking", false);
                Log.d(TAG, "tunnelIsWorking - " + tunnelIsWorking);
                updateButtonRun(null);
                MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    };
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onSaveInstanceState() called");

        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean("tunnelIsWorking", tunnelIsWorking);

        Button buttonRun =
                (Button)findViewById(R.id.buttonRun);
        savedInstanceState.putInt("buttonRunTextColor",
                buttonRun.getCurrentTextColor());
        savedInstanceState.putString("buttonRunText",
                ""+buttonRun.getText());
        savedInstanceState.putBoolean("buttonRunClickable",
                buttonRun.isClickable());
    }
    @Override
    protected void onStart() {//activity is started and visible to the user
        Log.d(TAG,"onStart() called");
        super.onStart();
    }
    @Override
    protected void onResume() {//activity was resumed and is visible again
        Log.d(TAG,"onResume() called");
        super.onResume();
    }
    @Override
    protected void onPause() { //device goes to sleep or another activity appears
        Log.d(TAG,"onPause() called");//another activity is currently running (or user has pressed Home)
        super.onPause();
    }
    @Override
    protected void onStop() { //the activity is not visible anymore
        Log.d(TAG,"onStop() called");
        super.onStop();
    }
    @Override
    protected void onDestroy() {//android has killed this activity
        Log.d(TAG,"onDestroy() called");
        super.onDestroy();

        unregisterReceiver(broadcastReceiver);
    }
    @Override
    public void onBackPressed() {  // Просто сворачивается, а не закрывается
        Log.d(TAG, "onBackPressed");

        moveTaskToBack(true);
    }

}
package com.gf169.reversehttptunnel;

import android.content.SharedPreferences;
import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Random;

public class SettingsActivity extends Activity {
    private static String TAG = "gfSettingsActivity";

    SharedPreferences prefs;
    String prefsStr;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Get the app's shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ((TextView) findViewById(R.id.server_origin))
                .setText(prefs.getString("server_origin",
                        this.getResources().getString(R.string.server_origin)));
        ((TextView) findViewById(R.id.server_timeout))
                .setText(""+prefs.getInt("server_timeout",
                        this.getResources().getInteger(R.integer.server_timeout)));
        ((TextView) findViewById(R.id.local_service))
                .setText(prefs.getString("local_service",
                        this.getResources().getString(R.string.local_service)));
        ((TextView) findViewById(R.id.local_service_timeout))
                .setText(""+prefs.getInt("local_service_timeout",
                        this.getResources().getInteger(R.integer.local_service_timeout)));
        ((TextView) findViewById(R.id.device_id))
                .setText(prefs.getString("device_id",formDeviceID()));

        Button buttonRun =
                (Button) findViewById(R.id.buttonSave);
        buttonRun.setOnClickListener(buttonSaveListener);
    }
    private View.OnClickListener buttonSaveListener = new
            View.OnClickListener() {
                public void onClick(View v) {
                    savePrefs();
                    finish();
                }
            };
    void savePrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_origin",
                ""+((TextView) findViewById(R.id.server_origin)).getText());
        editor.putInt("server_timeout",
                Integer.parseInt(""+((TextView) findViewById(R.id.server_timeout)).getText()));
        editor.putString("local_service",
                ""+((TextView) findViewById(R.id.local_service)).getText());
        editor.putInt("local_service_timeout",
                Integer.parseInt(""+((TextView) findViewById(R.id.local_service_timeout)).getText()));
        editor.putString("device_id",formDeviceID());
        editor.commit();
    }
    String formDeviceID() { // Случайная строка из 7 строчных латинских букв и цифр
        Random r=new Random();
        String s="";
        int j;
        for (int i=0; i<7; i++) {
            j=r.nextInt(35);
            if (j<10) {
                s = s + j;
            } else {
                s=s+(char)(87+j); // a-z
            }
        }
        return s;
    }
    static void iniPrefs(Activity activity) {  // Set defaults
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_origin",activity.getResources().getString(R.string.server_origin));
        editor.putInt("server_timeout",activity.getResources().getInteger(R.integer.server_timeout));
        editor.putString("local_service",activity.getResources().getString(R.string.local_service));
        editor.putInt("local_service_timeout",activity.getResources().getInteger(R.integer.local_service_timeout));
        editor.putString("device_id",activity.getResources().getString(R.string.local_service));
        editor.commit();
    }
}
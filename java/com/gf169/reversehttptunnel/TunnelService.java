package com.gf169.reversehttptunnel;


import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TunnelService extends Service {
    static final String TAG="gfTunnelService";

    volatile Boolean isWorking;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.ECLAIR) { // Неубиваемый сервис
            Notification notification = new Notification(R.mipmap.ic_launcher, "Reverse HTTP tunnel is running",
                                                        System.currentTimeMillis());
            notification.contentIntent =
                    PendingIntent.getActivity(
                            this, 1,
                            new Intent(this, MainActivity.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                            PendingIntent.FLAG_UPDATE_CURRENT);
            notification.contentView = new RemoteViews("com.gf169.reversehttptunnel", R.layout.activity_main);
            startForeground(1, notification);
        }
        isWorking=false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand "+intent);

        String action;
        if (intent==null) { // Андроид убил сервис и сейчас оживляет
            action = "Toggle state";
        } else {  // Нормальный вызов
            Bundle extras = intent.getExtras();
            action = extras.getString("Action");
        }
        if (action.equals("Toggle state")) {
            if (isWorking) {
                stop(1);
            } else {
                isWorking = true;
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                tunnel();
                            }
                        }).start();
            }
        }
        return Service.START_STICKY;
    };
    @Override
    public void onDestroy() {
        stop(0); // Вдруг снаружи кто-то сделает stopService
        Log.d(TAG, "onDestroy");
    }
    void stop(int callNumber) {
        Log.d(TAG, "stop "+callNumber);

        isWorking = false;
        if (callNumber==5) { // Exception
            reportState();   // Иначе вызовется в конце tunnel
        }
        stopSelf();
    }
    void reportState() { // Сообщение для MainActivity
        Intent intent = new Intent("gfMessageFromTunnelService");
        intent.putExtra("action","report service state");
        intent.putExtra("isWorking",isWorking);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }


    String localUrlTail;
    String queryMethod;
    String requestId;
    String responseStatus;
    String responseHeaders;
    String sError=null;
    String sErrorLoc=null;
    int maxBodySize=10000000; // todo
    int realBodySize;
    byte[] body;
    SharedPreferences prefs;

    void tunnel() {
        Log.d(TAG, "Tunnel service is working");
        reportState();

        localUrlTail=null;
        queryMethod=null;
        requestId=null;
        while (isWorking) {
            OnePair();
        }

        reportState();
    }
    void OnePair() {
        int nAttempts=1000;
        int delayBetweenAttempts=3;
        for (int iAttempt = 0; iAttempt < nAttempts; iAttempt++) {
            if (!isWorking) break;
            prefs = PreferenceManager.getDefaultSharedPreferences(this); // можно менять на ходу

            makeRemoteRequest(); // Отправляем на сервер результат предыдущего localRquest,
                                 // получаем id следующего запроса, method и url
            if (sError == null
//                    || sError.startsWith("java.net.SocketTimeoutException")
               )
                break;
            else {
                Log.d(TAG, "Запрос завершился с ошибкой - повторим (" + (iAttempt + 2) + ") через "
                        + delayBetweenAttempts + " секунд");
                sleep(delayBetweenAttempts * 1000, true);
            }
        }
        if (sError!=null) return; // Ничего поделать не можем - отваливаемся
//queryMethod="GET"; localUrlTail="/js/ipwebcam_override.js";

        makeLocalRequest(); // Выполняем запрос к локальному сервису
        sErrorLoc=sError;
    }
    void makeRemoteRequest() {
        Log.i(TAG, "makeRemoteRequest, requestId=" + requestId);

        sError = null;

        StringBuffer jsonBuf = new StringBuffer();
        HttpURLConnection con = null;

        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "gf169nmv65457gfhb6t5nnmd787sert645";

        try {
            String sURL = prefs.getString("server_origin", "НЕ_ЗАДАН_SERVER_ORIGIN"); // "http://104.131.44.48:3000";
            URL url = new URL(sURL + "/tunnel/handle-request");
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(prefs.getInt("server_timeout", 0) * 1000);
            con.setReadTimeout(prefs.getInt("server_timeout", 0) * 1000);
            con.setDoInput(true);
            con.setDoOutput(true);

            if (requestId != null) {  // Перед этим был выполненен LocalRequest - шлем результат
                Log.i(TAG, "makeRemoteRequest, sending response on request " + requestId +
                        ",method " + queryMethod + ", file " + localUrlTail);
                con.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                con.addRequestProperty("X-Request-ID", requestId);
                con.addRequestProperty("Device-ID", prefs.getString("device_id", "НЕ_ЗАДАН_DEVICE_ID"));

                Log.d(TAG, "makeRemoteRequest, multipart body:");

                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                String s;
                // status
                if (responseStatus != null) {
                    s = twoHyphens + boundary + lineEnd
                            + "Content-Disposition: form-data; name=\"status\""
                            + lineEnd + lineEnd + responseStatus + lineEnd;
                    out.writeBytes(s);
                    Log.d(TAG, s);
                }
                // headers
                if (responseHeaders != null) {
                    s = twoHyphens + boundary + lineEnd
                            + "Content-Disposition: form-data; name=\"headers\""
                            + lineEnd + lineEnd + responseHeaders + lineEnd;
                    out.writeBytes(s);
                    Log.d(TAG, s);
                }
                if (sErrorLoc != null) {  // Ошибка локального сервиса
                    s = twoHyphens + boundary + lineEnd
                            + "Content-Disposition: form-data; name=\"error\""
                            + lineEnd + lineEnd + sErrorLoc + lineEnd
                            + twoHyphens + boundary + twoHyphens + lineEnd;
                    out.writeBytes(s);
                    Log.d(TAG, s);
                } else { // data - body ответа локального сервиса
                    s = twoHyphens + boundary + lineEnd
                            + "Content-Disposition: form-data; name=\"data\";filename=\""
                            + "unicycle.jpg" + "\"" + lineEnd + lineEnd;
                    out.writeBytes(s);
                    Log.d(TAG, s);

                    if (realBodySize > 0) {
                        out.write(body, 0, realBodySize);
                        Log.d(TAG, "Здесь само тело - " + realBodySize + " байт");
//                        s=new String(body);
//                        Log.d(TAG, s);
                    }
                    s = lineEnd + // конец куска
                            twoHyphens + boundary + twoHyphens + lineEnd; // конец тела
                    out.writeBytes(s);
                    Log.d(TAG, s);
                    Log.i(TAG, "makeRemoteRequest, file " + localUrlTail + ": sent bytes " + realBodySize);
                }
                out.flush();
                out.close();
            }
            // Читаем ответ - параметры следующего запроса
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String s;
            while ((s = in.readLine()) != null) {
                jsonBuf.append(s);
            }
            Log.d(TAG, "makeRemoteRequest, response jsonBuf=" + jsonBuf);

            JSONObject json = new JSONObject(jsonBuf.toString());
            String requestId2 = requestId;
            requestId = json.getString("id");
            localUrlTail = json.getString("url");
            queryMethod = json.getString("method");

            Log.i(TAG, "makeRemoteRequest, received response with parameters of the next query: " +
                    " requestId " + requestId + ",method " + queryMethod + ", file " + localUrlTail);

            if (con.getResponseCode() != 200) {
                requestId = requestId2;
                sError = "Response code " + con.getResponseCode() + " " + con.getResponseMessage();
                in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
                jsonBuf.setLength(0);
                while ((s = in.readLine()) != null) {
                    jsonBuf.append(s);
                }
                sError = sError + "\n" + jsonBuf;
            }
        } catch (JSONException e) {
            sError = e.toString();
        } catch (IOException e) {
            sError = e.toString();
        }
        if (con != null) con.disconnect();
        if (sError != null) {
            Log.i(TAG, "makeRemoteRequest, error :" + sError);
        }
    }
    void makeLocalRequest() {
        Log.i(TAG, "makeLocalRequest, queryMethod="+queryMethod+" localUrlTail="+localUrlTail);

        sError=null;
        body=null;
        realBodySize=0;

        HttpURLConnection con=null;
        try {
            URL url = new URL(prefs.getString("local_service","НЕ_ЗАДАН_LOCAL_SERVICE")
                    +localUrlTail);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(queryMethod);
            con.setConnectTimeout(prefs.getInt("local_service_timeout",0)*1000);
            con.setReadTimeout(prefs.getInt("local_service_timeout",0)*1000);
/*
            con.setRequestProperty("Accept-Encoding","gzip, deflate");
            con.setRequestProperty("Accept-Language","ru-RU,ru;q=0.8,en-US;q=0.6,en;q=0.4");
            con.setRequestProperty("Cache-Control","no-cache");
            con.setRequestProperty("connection","keep-alive");
            con.setRequestProperty("x-requested-with","XMLHttpRequest");
            con.setRequestProperty("Content-Length","20000");
*/
            con.setDoInput(true);

            responseStatus = "" + con.getResponseCode();
            if (con.getResponseCode() != 200) {
                sError = "" + con.getResponseCode() + " " + con.getResponseMessage();
            } else {
                JSONObject json = new JSONObject();
                for (int i = 1; con.getHeaderField(i) != null; i++) {
                    json.put(con.getHeaderFieldKey(i), con.getHeaderField(i));
                }
                responseHeaders = json.toString();
                Log.d(TAG, "makeLocalRequest, responseHeaders="+responseHeaders);

                realBodySize = con.getContentLength(); // Может врать, говорить меньше, чем на самом деле
                Log.d(TAG, "makeLocalRequest, content length returned by server="+realBodySize);
                    realBodySize=-1;

                if (realBodySize < 0) realBodySize = maxBodySize;
                if (realBodySize > maxBodySize) {
                    sError = "Local service is going to return too big body: "
                            + realBodySize + " bytes, while maximum allowed - " + maxBodySize;
                } else {
                    DataInputStream in = new DataInputStream(con.getInputStream());
                    // con.getInputStream() объект класса InputStream, который абстрактный !!!
                    body = new byte[maxBodySize];
                    realBodySize = 0;
                    while (realBodySize<maxBodySize) {
                        int i = in.read(body, realBodySize, maxBodySize - realBodySize);
                        if (i > 0) {
                            realBodySize += i;
                        } else {
                            break;
                        }
                    };
                    Log.d(TAG, "makeLocalRequest, response body length=" + realBodySize);
                }
            }
        } catch (JSONException e) {
            sError = e.toString();
        } catch (IOException e) {
            sError = e.toString();
        }
        if (con != null) con.disconnect();
        Log.i(TAG, "makeLocalRequest result: "+ (sError==null ? "OK" : sError));
        if (sError==null) {
            Log.i(TAG, "makeLocalRequest, file " + localUrlTail + ": received bytes "+ (body==null ? "0" : realBodySize));
        }
    }
    static boolean sleep(int milliseconds, boolean interruptable) {
        long endTime = SystemClock.elapsedRealtime() + milliseconds;
        while (endTime > SystemClock.elapsedRealtime()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                if (interruptable) {
                    return true; // interrupted
                }
            }
        }
        return false;  // Проспал сколько было сказано
    }
}
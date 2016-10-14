package com.salestrack.salestrack.Networking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OkHttpRequest {
    OkHttpClient client = new OkHttpClient();
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public OkHttpRequest(OkHttpClient client){
        this.client = client;
    }

    public Call POST(String url, HashMap<String, String> parameters, Callback callback) {
        FormBody.Builder builder = new FormBody.Builder();
        Iterator it = parameters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            builder.add(pair.getKey().toString(), pair.getValue().toString());
        }

        RequestBody formBody = builder.build();
        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();


        Call call  = client.newCall(request);
        call.enqueue(callback);
        return call;
    }

    public Call GET(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Call call  = client.newCall(request);
        call.enqueue(callback);
        return call;
    }
}

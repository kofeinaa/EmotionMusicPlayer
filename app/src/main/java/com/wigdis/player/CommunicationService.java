package com.wigdis.player;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import static java.lang.Thread.sleep;

public class CommunicationService {

    private static CommunicationService service;
    private static final String url = "http://sapphire.memleak.pl:8080";
    private static String id;

    private CommunicationService(){}

    public static CommunicationService getService(){
        if (service == null){
            service = new CommunicationService();
        }

        return service;
    }

    private void getRequestCall(Context from){
        // returns first track -GET METHOD REQUEST

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            id = (String)response.get("id");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        System.out.println("Error");

                    }
                });
        Volley.newRequestQueue(from).add(jsObjRequest);
    }

    public String getFirstTrack(Context from){
        getRequestCall(from);
        //sleep(1000);
        return id;
    }

    public void getNextTrackId(){
        // returns next track id - POST METHOD REQUEST

    }
}

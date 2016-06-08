package org.inspira.emag.networking;

import android.content.Context;
import android.util.Log;

import org.inspira.emag.actividades.MainActivity;
import org.inspira.emag.actividades.OrganizarVehiculos;
import org.inspira.emag.database.TripsData;
import org.inspira.emag.gps.MyLocationProvider;
import org.inspira.emag.shared.ThrottlePos;
import org.inspira.emag.shared.User;
import org.inspira.emag.shared.Vehiculo;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.inspira.emag.shared.Location;
import org.inspira.emag.shared.RPM;
import org.inspira.emag.shared.RawReading;
import org.inspira.emag.shared.Shareable;
import org.inspira.emag.shared.Speed;
import org.inspira.emag.shared.Trip;

/**
 * Created by jcapiz on 22/12/15.
 */
public class Uploader extends Thread {

    private static final String TAG = "Uploader";
    private Shareable[] readings;
    private Context ctx;
    private String email;
    private String vehiculo;

    public Uploader(Shareable... params){
        readings = params;
    }

    public void setContext(Context ctx){
        this.ctx = ctx;
        TripsData db = new TripsData(ctx);
        email = db.getUserData().getEmail();
        vehiculo = ctx.getSharedPreferences(OrganizarVehiculos.class.getName(), Context.MODE_PRIVATE)
                .getString("vehiculo", "NaN");
    }

    @Override
    public void run(){
        Log.d(TAG, "Uploading " + readings.length + " readings..");
        try {
            JSONObject json = new JSONObject();// {"vehiculo": "BMW", "email": "alan@emag.com", "action": 4, "Latitud": -19.15433543248635341, "Longitud": 99.0145314886145, "Timestamp": "18/02/2016 19:40:32"}
            json.put("vehiculo", vehiculo);
            json.put("email", email);
            for (Shareable reading : readings) { // for(int i=0; i<readings.length; i++){ Shareable reading = readings[i];
                HttpURLConnection con = (HttpURLConnection) new URL(MainActivity.SERVER_URL).openConnection();
                con.setDoOutput(true);
                DataOutputStream salida = new DataOutputStream(con.getOutputStream());
                if( reading instanceof Location)
                    try {
                        json.put("action", 4);
                        json.put("Latitud", ((Location) reading).getLatitud());
                        json.put("Longitud", ((Location) reading).getLongitud());
                        json.put("timestamp", ((Location) reading).getTimestamp());
                    } catch (JSONException re) {
                        Log.e(TAG, re.toString());
                    }
                else if(reading instanceof RPM)
                    try {
                        json.put("action", 2);
                        json.put("RPM", ((RPM) reading).getRpmValue());
                        json.put("timestamp", ((RPM) reading).getTimeStamp());
                    } catch (JSONException re) {
                        Log.e(TAG, re.toString());
                    }
                else if( reading instanceof Speed )
                    try {
                        json.put("action", 3);
                        json.put("SPEED", ((Speed) reading).getSpeed());
                        json.put("timestamp", ((Speed) reading).getTimestamp());
                    } catch (JSONException re) {
                        Log.e(TAG, re.toString());
                    }
                else if(reading instanceof Trip )
                    try {
                        json.put("action",1);
                        json.put("fechaInicio", (((Trip) reading).getFechaInicio()));
                        try {
                            json.put("fechaFin", (((Trip) reading).getFechaFin()));
                        }catch(NullPointerException e){
                            Log.d(TAG,e.getMessage());
                        }
                    } catch (JSONException re) {
                        Log.e(TAG, re.toString());
                    }
                else if(reading instanceof ThrottlePos)
                    try{
                        json.put("action", 6);
                        json.put("PdA", ((ThrottlePos)reading).getThrottlePos());
                        json.put("timestamp", ((ThrottlePos) reading).getTimestamp());
                    }catch(JSONException e){
                        Log.e(TAG, e.getMessage());
                    }
                else if(reading instanceof RawReading)
                    try {
                        json = ((RawReading) reading).getJson();
                        json.put("action", -3);
                    }catch(JSONException e){
                        e.printStackTrace();
                    }
                Log.d(TAG,"Sending something:\n" + json.toString());
                salida.write(json.toString().getBytes());
                salida.flush();
                DataInputStream entrada = new DataInputStream(con.getInputStream());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int length;
                byte[] chunk = new byte[512];
                while((length = entrada.read(chunk))!=-1)
                    baos.write(chunk,0,length);
                Log.d(TAG, baos.toString());
                if(baos.toString().equals("OK") && ! (reading instanceof RawReading || reading instanceof Trip || reading instanceof User || reading instanceof Vehiculo) ) {
                    reading.commitEntry(ctx);
                    Log.d(TAG, "Commiting something // " + baos.toString());
                }
                salida.close();
                con.disconnect();
            }
        }catch(JSONException | IOException e){
            e.printStackTrace();
        }
        Log.d(TAG, "Done");
    }
}

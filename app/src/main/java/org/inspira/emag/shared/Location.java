package org.inspira.emag.shared;

import android.content.Context;

import org.inspira.emag.database.TripsData;

/**
 * Created by jcapiz on 18/11/15.
 */
public class Location implements Shareable{

    private int idValue;
    private String latitud;
    private String longitud;
    private String timestamp;
    private int idTrip;

    public Location(int idValue, String latitud, String longitud, String timestamp, int idTrip) {

        this.idValue = idValue;
        this.latitud = latitud;
        this.longitud = longitud;
        this.timestamp = timestamp;
        this.idTrip = idTrip;
    }

    public int getIdValue() {
        return idValue;
    }

    public String getLatitud() {
        return latitud;
    }

    public String getLongitud() {
        return longitud;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getIdTrip() {
        return idTrip;
    }

    @Override
    public void commitEntry(Context ctx) {
        TripsData db = new TripsData(ctx);
        db.setCommited("Location",idTrip);
    }
}
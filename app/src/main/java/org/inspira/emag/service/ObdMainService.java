package org.inspira.emag.service;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.github.pires.obd.enums.ObdProtocols;
import com.github.pires.obd.exceptions.ResponseException;

import org.capiz.bluetooth.R;
import org.inspira.emag.actividades.MainActivity;
import org.inspira.emag.actividades.OrganizarVehiculos;
import org.inspira.emag.actividades.ProveedorDeRecursos;
import org.inspira.emag.bluetooth.BluetoothManager;
import org.inspira.emag.database.TripsData;
import org.inspira.emag.gps.MyLocationProvider;
import org.inspira.emag.networking.Uploader;
import org.inspira.emag.shared.RPM;
import org.inspira.emag.shared.Shareable;
import org.inspira.emag.shared.Speed;
import org.inspira.emag.shared.ThrottlePos;
import org.inspira.emag.shared.Trip;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObdMainService extends Service {

    private MyLocationProvider mlp;
    private NotificationManager mNM;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private static final int SERVICIO_EMAG = 1234;
    private static final int UPDATING_ID = 21;
    private boolean finishedConnection = false;
    private final IBinder mBinder = new LocalBinder();
    private Activity mActivity;
    private NotificationCompat.Builder mBuilder;
    private ConcurrentLinkedQueue<Runnable> updates;
    private int startId;

    public class LocalBinder extends Binder {
        public ObdMainService getService() {
            return ObdMainService.this;
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {

        private boolean TRUE = true;
        private BluetoothSocket socket;

        public ServiceHandler(Looper looper) {
            super(looper);
        }

        public void stopLectures() {
            TRUE = false;
        }

        public void beginConnection(BluetoothDevice btDev) throws IOException {
            BluetoothSocket temp;
            try {
                socket = btDev.createRfcommSocketToServiceRecord(UUID.fromString(BluetoothManager.MY_UUID));
                Log.d("Melchor", "Connecting...");
                socket.connect();
                Log.d("Melchor", "Connected, preparing streams...");
            } catch (IOException e) {
                Log.e("Tulman", "There was an error while establishing Bluetooth connection. Falling back..", e);
                Class<?> clazz = socket.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
                try {
                    Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                    Object[] params = new Object[]{Integer.valueOf(1)};
                    temp = (BluetoothSocket) m.invoke(socket.getRemoteDevice(), params);
                    temp.connect();
                    socket = temp;
                } catch (Exception e2) {
                    Log.e("TulmanSan", "Couldn't fallback while establishing Bluetooth connection. Stopping app..", e2);
                    throw new IOException();
                }
                e.printStackTrace();
            }
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                beginConnection(
                        BluetoothAdapter.getDefaultAdapter()
                                .getRemoteDevice((String) msg.obj)
                );
                TripsData db = new TripsData(ObdMainService.this);
                String tiempo = ProveedorDeRecursos.obtenerFecha();
                int rid = db.insertTrip(db.obtenerIdVehiculoFromNombre(ProveedorDeRecursos
                                .obtenerRecursoString(ObdMainService.this, "vehiculo")), tiempo);
                Trip t = new Trip(rid, tiempo, null);
                Uploader u = new Uploader(t);
                u.setContext(ObdMainService.this);
                u.start();
                Log.d("Falchor", "A new trip begins (TRIP_ID: " + rid + ")");
                runCommand(new EchoOffCommand());
                runCommand(new LineFeedOffCommand());
                runCommand(new TimeoutCommand(1500));
                runCommand(new SelectProtocolCommand(ObdProtocols.AUTO));
                Log.d("Surrender", "SUCCESS");
                while (TRUE) {
                    runCommand(new RPMCommand());
                    runCommand(new SpeedCommand());
                    runCommand(new ThrottlePositionCommand());
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Melchor", "Didn't survive");
            } finally {
                stopActions();
            }
        }

        private void runCommand(final ObdCommand cmd) {
            try {
                cmd.run(socket.getInputStream(), socket.getOutputStream());
                Log.d("Melchor",cmd.getName() + ": " + cmd.getFormattedResult());
                TripsData td = new TripsData(ObdMainService.this);
                Trip trip = td.getUnconcludedTrip();
                Shareable value = null;
                String time = ProveedorDeRecursos.obtenerFecha();
                if (cmd.getName().equals(AvailableCommandNames.SPEED.getValue())) {
                    if(mActivity != null ) mActivity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((MainActivity)mActivity).setSpeedText(cmd.getFormattedResult());
                                }
                            }
                    );
                    int lrid = td.insertaVelocidad(cmd.getFormattedResult(), time, trip.getIdTrip());
                    value = new Speed(lrid, cmd.getFormattedResult(), time, trip.getIdTrip());
                } else if (cmd.getName().equals(AvailableCommandNames.ENGINE_RPM.getValue())) {
                    if(mActivity != null ) mActivity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((MainActivity)mActivity).setRpmText(cmd.getFormattedResult());
                                }
                            }
                    );
                    int lrid = td.insertaRPM(cmd.getFormattedResult(), time, trip.getIdTrip());
                    value = new RPM(lrid, cmd.getFormattedResult(), time, trip.getIdTrip());
                } else if (cmd.getName().equals(AvailableCommandNames.THROTTLE_POS.getValue())) {
                    if(mActivity != null ) mActivity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((MainActivity)mActivity).setPdaText(cmd.getFormattedResult());
                                }
                            }
                    );
                    int lrid = td.insertaThrottlePos(cmd.getFormattedResult(), time, trip.getIdTrip());
                    value = new ThrottlePos(lrid, cmd.getFormattedResult(), time, trip.getIdTrip());
                }
                if (value != null) {
                    Uploader up = new Uploader(value);
                    up.setContext(ObdMainService.this);
                    up.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
                stopActions();
            } catch(ResponseException e){
                e.printStackTrace();
            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }

        private void stopActions() {
            mNM.cancel(SERVICIO_EMAG);
            if(mlp != null && mlp.isConnected())
                mlp.stopLocationUpdates();
            stopLectures();
            if(socket != null)
            try {
                socket.close();
            } catch (IOException e) {
            }
            if(mActivity != null)
            mActivity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            ((MainActivity) mActivity).turnThingsOff();
                            ((MainActivity) mActivity).makeSnackbar("Servicio detenido");
                        }
                    }
            );
            stopSelf();
        }
    }

    public void stopOperations() {
        if(mServiceHandler != null)
            mServiceHandler.stopLectures();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.startId = startId;
        // If we get killed, after returning from here, restart (START_STICKY)
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        mlp = new MyLocationProvider();
        updates = new ConcurrentLinkedQueue<>();
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        mActivity = null;
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopOperations();
        if(mlp != null && mlp.isConnected())
            mlp.stopLocationUpdates();
        if(mNM != null)
            mNM.cancel(SERVICIO_EMAG);
	}

	@Override
	public IBinder onBind(Intent intent) {
        return mBinder;
	}

    public void makeNotification(){
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.logo)
                        .setOngoing(true)
                        .setContentTitle("Servicio EMAG")
                        .setContentText("Estamos tomando lecturas del auto");
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);
        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        //mBuilder.setContentIntent(resultPendingIntent);
        mBuilder.setVibrate(new long[]{100, 100, 100, 600});
        mNM.notify(SERVICIO_EMAG, mBuilder.build());
    }

    public void setActivity(Activity mActivity){
        this.mActivity = mActivity;
        makeNotification();
        SharedPreferences sp =
                getSharedPreferences(MainActivity.class.getName(), Context.MODE_PRIVATE);
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = sp.getString("device_addr", "NaN");
        mServiceHandler.sendMessage(msg);
        int permissionCheck = ContextCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            mlp.setActivity(this.mActivity);
            mlp.createService();
        }else{
            mActivity.runOnUiThread(new Runnable(){
                @Override
                public void run(){
                    ((MainActivity)ObdMainService.this.mActivity).makeSnackbar("Necesitamos permiso de " +
                            "ubicación");
                }
            });
        }
        Log.d("Savior", "Activity successfully set");
    }

    public void sendUncommitedData(){
        updatingNotification();
    }

    private void updatingNotification(){
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.logo)
                        .setOngoing(true)
                        .setContentTitle("Sincronización")
                        .setContentText("Actualizando datos");
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        int incr=0;
                        // Sets the progress indicator to a max value, the
                        // current completion percentage, and "determinate"
                        // state
                        //mBuilder.setProgress(uncommitedTrips.length, incr++, false);
                        // Displays the progress bar for the first time.
                        mNM.notify(0, mBuilder.build());
                        // When the loop is finished, updates the notification
                        mBuilder.setContentText("¡Listo!")
                                // Removes the progress bar
                                .setProgress(0,0,false);
                        mNM.notify(UPDATING_ID, mBuilder.build());
                    }
                }
        ).start();
        //mBuilder.setContentIntent(resultPendingIntent);
        mBuilder.setVibrate(new long[]{100, 100, 100, 600});
        mNM.notify(UPDATING_ID, mBuilder.build());
    }
}
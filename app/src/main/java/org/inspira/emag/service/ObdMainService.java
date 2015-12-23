package org.inspira.emag.service;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.github.pires.obd.exceptions.ResponseException;

import org.capiz.bluetooth.R;
import org.inspira.emag.bluetooth.BluetoothManager;
import org.inspira.emag.bluetooth.CustomBluetoothActivity;
import org.inspira.emag.gps.MyLocationProvider;
import org.inspira.emag.networking.Uploader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.inspira.emag.database.TripsData;
import org.inspira.emag.shared.RPM;
import org.inspira.emag.shared.Shareable;
import org.inspira.emag.shared.Speed;
import org.inspira.emag.shared.Trip;

public class ObdMainService extends Service {

    private MyLocationProvider mlp;
    private NotificationManager mNM;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private static final int SERVICIO_EMAG = 1234;
    private boolean finishedConnection = false;
    private final IBinder mBinder = new LocalBinder();
    private Activity mActivity;
    private NotificationCompat.Builder mBuilder;
    private ConcurrentLinkedQueue<Runnable> updates;

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
                Log.d("Falchor", "A new trip begins (TRIP_ID: " + db.insertTrip(new Date()) + ")");
                runCommand(new EchoOffCommand());
                runCommand(new LineFeedOffCommand());
                runCommand(new TimeoutCommand(1000));
                runCommand(new AmbientAirTemperatureCommand());
                while (TRUE) {
                    runCommand(new RPMCommand());
                    runCommand(new SpeedCommand());
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
                if (cmd.getName().equals(AvailableCommandNames.SPEED.getValue())) {
                    mActivity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((CustomBluetoothActivity)mActivity).setSpeedText(cmd.getFormattedResult());
                                }
                            }
                    );
                    int lrid = td.insertaVelocidad(cmd.getFormattedResult(), trip.getIdTrip());
                    value = new Speed(lrid, cmd.getFormattedResult(), new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date()), trip.getIdTrip());
                } else if (cmd.getName().equals(AvailableCommandNames.ENGINE_RPM.getValue())) {
                    mActivity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    ((CustomBluetoothActivity)mActivity).setRpmText(cmd.getFormattedResult());
                                }
                            }
                    );
                    int lrid = td.insertaRPM(cmd.getFormattedResult(), trip.getIdTrip());
                    value = new RPM(lrid, cmd.getFormattedResult(), new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date()), trip.getIdTrip());
                }
                if (value != null)
                    new Uploader(value).start();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                stopActions();
            } catch(ResponseException e){
                e.printStackTrace();
            }
        }

        private void stopActions() {
            mNM.cancel(SERVICIO_EMAG);
            stopLectures();
            try {
                socket.close();
            } catch (IOException e) {
            }
            mActivity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            ((CustomBluetoothActivity) mActivity).turnThingsOff();
                            Toast.makeText(mActivity,"Servicio detenido",Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            stopSelf();
        }
    }

    public void stopOperations() {
        mServiceHandler.stopLectures();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the
        // job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent.getStringExtra("device_addr");
        mServiceHandler.sendMessage(msg);
        makeNotification();
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
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopOperations();
        mlp.stopLocationUpdates();
        mNM.cancel(SERVICIO_EMAG);
	}

	@Override
	public IBinder onBind(Intent intent) {
		 return mBinder;
		//return null;
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
        Intent resultIntent = new Intent(this, CustomBluetoothActivity.class);
        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(CustomBluetoothActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        mBuilder.setVibrate(new long[]{100, 100, 100, 600});
        mNM.notify(SERVICIO_EMAG, mBuilder.build());
    }

    public void setActivity(final Activity mActivity){
        this.mActivity = mActivity;
        mlp.setActivity(this.mActivity);
        mlp.createService();
    }

	public void hasFinished() {
		while (!finishedConnection);
	}
}
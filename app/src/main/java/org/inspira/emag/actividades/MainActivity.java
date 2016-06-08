package org.inspira.emag.actividades;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.capiz.bluetooth.R;
import org.inspira.emag.bluetooth.BluetoothManager;
import org.inspira.emag.console.ConsoleActivity;
import org.inspira.emag.database.TripsData;
import org.inspira.emag.networking.CommitTrip;
import org.inspira.emag.networking.Uploader;
import org.inspira.emag.service.ObdMainService;
import org.inspira.emag.service.ObdMockService;
import org.inspira.emag.shared.Location;
import org.inspira.emag.shared.RawReading;
import org.inspira.emag.shared.Speed;
import org.inspira.emag.shared.ThrottlePos;
import org.inspira.emag.shared.Trip;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * Created by jcapiz on 15/09/15.tl
 */
public class MainActivity extends AppCompatActivity {

    public static final String DIR_VIAJE = Environment.getExternalStorageDirectory().getAbsolutePath() + "/EMAG/Viajes";
    public static final String DIR_RPM = Environment.getExternalStorageDirectory().getAbsolutePath() + "/EMAG/RPMs";
    public static final String DIR_VELOCIDAD = Environment.getExternalStorageDirectory().getAbsolutePath() + "/EMAG/Velocidades";
    public static final String DIR_THROTTLE_POS = Environment.getExternalStorageDirectory().getAbsolutePath() + "/EMAG/Posiciones_Acel";
    public static final String DIR_UBICACION = Environment.getExternalStorageDirectory().getAbsolutePath() + "/EMAG/Ubicaciones";
    public static final String VIAJE = "EMAG_Viajes.csv";
    public static final String RPM = "EMAG_rpms.csv";
    public static final String THROTTLE_POS =  "EMAG_PosicionesAcel.csv";
    public static final String VELOCIDAD =  "EMAG_Velocidades.csv";
    public static final String UBICACION = "EMAG_Ubicaciones.csv";
	private static final int START_CLIENT_ACTION = 2;
    public static final String SERVER_URL = "http://www.zooropa.com.mx/services/";
    private static final int PREPARACION = 324;
    private Button clientMode;
	private TextView buttonLabel;
    private TextView mLatitudeText;
    private TextView mLongitudeText;
    private TextView speedText;
    private TextView rpmText;
    private TextView pdaText;
	private BluetoothManager manager;
	private Intent mService;
	private int backButtonCount = 1;
    private boolean serviceOn = false;
	private boolean serverActionInProgress = false;
    private Intent mServiceMock;

    private void launchAction(int typeAction) {
		backButtonCount = 1;
		serverActionInProgress = true;
		switch (typeAction) {
		case START_CLIENT_ACTION:
			Intent i = new Intent(this, DevicePickerActivity.class);
			startActivityForResult(i, START_CLIENT_ACTION);
            serviceOn = true;
            clientMode.setBackgroundResource(R.drawable.on_button);
			break;
		default:
		}
	}

    private void launchConsole(){
        Intent i = new Intent(this, ConsoleActivity.class);
        startActivity(i);
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bluetooth_activity);
		clientMode = (Button) findViewById(R.id.client_mode);
		buttonLabel = (TextView) findViewById(R.id.welcome);
        mLatitudeText = (TextView) findViewById(R.id.mLatitudeText);
        mLongitudeText = (TextView) findViewById(R.id.mLongitudeText);
        speedText = (TextView) findViewById(R.id.speed_value);
        rpmText = (TextView) findViewById(R.id.rpm_value);
        pdaText = (TextView) findViewById(R.id.pda_value);
		manager = new BluetoothManager(this);
		mService = new Intent(this, ObdMainService.class);
        mServiceMock = new Intent(this, ObdMockService.class);
        TextView verResultados = (TextView) findViewById(R.id.bluetooth_activity_ver_resultados);
        verResultados.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View view){
                Uri location = Uri.parse("http://www.emag.zooropa.com.mx");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW    , location);
                // Verify it resolves
                PackageManager packageManager = getPackageManager();
                List<ResolveInfo> activities = packageManager.queryIntentActivities(mapIntent, 0);
                boolean isIntentSafe = activities.size() > 0;
                // Start an activity if it's safe
                if (isIntentSafe) {
                    startActivity(mapIntent);
                }
            }
        });
        ((TextView)findViewById(R.id.welcome)).setTypeface(Typeface.createFromAsset(getAssets(),
                "RobotoCondensed/RobotoCondensed-Regular.ttf"));
		if (manager.getBluetoothAdapter() == null) {
			Toast.makeText(this, "Bluetooth no disponible X.X",
					Toast.LENGTH_SHORT).show();
		} else {
			clientMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!manager.isBluetoothEnabled())
                        manager.enableBluetooth();
                    else if (!serviceOn)
                        launchAction(START_CLIENT_ACTION);
                    else {
                        doUnbindService();
                        stopService(mService);
                        clientMode.setBackgroundResource(R.drawable.off);
                        makeSnackbar("Lectura de datos detenida");
                        serviceOn = !serviceOn;
                    }
                }
            });
		}
	}

    public void makeSnackbar(String message){
        Snackbar.make(clientMode, message, Snackbar.LENGTH_SHORT)
                .setAction("Aviso", null).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem actionSyncData = menu.findItem(R.id.action_sync_data);
        TripsData db = new TripsData(this);
        Log.d("Legitimo", "We got to see " + db.getUncommitedTrips().length + " uncommited trips.");
        if(db.getUncommitedTrips().length == 0) {
            actionSyncData.setVisible(false);
            actionSyncData.setEnabled(false);
        }else {
            actionSyncData.setVisible(true);
            actionSyncData.setEnabled(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_go_console) {
            launchConsole();
        } else if (id == R.id.action_sync_data){
            sendUncommitedData();
        } else if (id == R.id.action_export_data){
            grabReport();
        }else if(id == R.id.action_settings){
            launchConfiguraPerfiles();
        }else if(id == R.id.action_test_file_output){
            testFileOutput();
        }
        return true;
    }

    private boolean serviceOnMock;
    private ObdMockService mBoundServiceMock;
    private ServiceConnection mConnectionMock = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mIsBoundMock = true;
            serviceOnMock = true;
            mBoundServiceMock = ((ObdMockService.LocalBinder) service).getService();
            mBoundServiceMock.setActivity(MainActivity.this);
            Uploader up = new Uploader(new RawReading("Acabamos de ponerle algo aquí " + ProveedorDeRecursos.obtenerFecha()));
            up.setContext(MainActivity.this);
            up.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundServiceMock = null;
            serviceOnMock = false;
        }
    };
    private boolean mIsBoundMock;

    void doBindServiceMock() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!mIsBoundMock) {
            bindService(mServiceMock, mConnectionMock,
                    Context.BIND_AUTO_CREATE);
            Log.d("DBZ", "Bounded");
        }
    }

    public void doUnbindServiceMock() {
        if (mIsBoundMock) {
            // Detach our existing connection.
            unbindService(mConnectionMock);
            mIsBoundMock = false;
            Log.d("DBZ","unBounded");
        }
    }

    private void testFileOutput() {
        startService(mServiceMock);
        doBindServiceMock();
        Log.d("Main Activity", "Mock Started");
    }

    private void launchConfiguraPerfiles() {
        startActivity(new Intent(this, OrganizarVehiculos.class));
    }

    @Override
    protected void onPostResume(){
        super.onPostResume();
        revisarDatosDeUsuario();
    }

    private void revisarDatosDeUsuario() {
        TripsData db = new TripsData(this);
        if(!db.userCheck())
            iniciaRegistro();
    }

    private void iniciaRegistro() {
        startActivityForResult(new Intent(this, Preparacion.class), PREPARACION);
    }

    private void commitTrip(int idTrip){
        new CommitTrip(this,idTrip).start();
    }

    public void sendUncommitedData(){
        new Thread() {

            @Override public void run() {
                runOnUiThread(new Runnable(){
                    @Override public void run(){
                        makeSnackbar("Sincronizando datos...");
                    }
                });
                TripsData db = new TripsData(MainActivity.this);
                Trip[] uncommitedTrips = db.getUncommitedTrips();
                Uploader up = new Uploader(uncommitedTrips);
                up.setContext(MainActivity.this);
                up.start();
                Log.d("Syncer", "-->" + uncommitedTrips.length);
                for (Trip trip : uncommitedTrips) {
                    Uploader upl = new Uploader(db.getLocationsByTrip(trip.getIdTrip()));
                    upl.setContext(MainActivity.this);
                    upl.start();
                    Uploader upt = new Uploader(db.getRPMsByTrip(trip.getIdTrip()));
                    upt.setContext(MainActivity.this);
                    upt.start();
                    Uploader ups = new Uploader(db.getSpeedsByTrip(trip.getIdTrip()));
                    ups.setContext(MainActivity.this);
                    ups.start();
                    if (db.getLocationsByTrip(trip.getIdTrip()).length == 0
                            && db.getRPMsByTrip(trip.getIdTrip()).length == 0
                            && db.getSpeedsByTrip(trip.getIdTrip()).length == 0) {
                        commitTrip(trip.getIdTrip());
                    } else
                        Log.d("EMAG sendData", "Tip " + trip.getIdTrip() + "unconcluded.");
                }
            }
        }.start();
    }

    public void grabReport(){
        new Thread() {
            @Override
            public void run() {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            makeSnackbar("Estamos exportando los datos...");
                        }
                    });
                    Date cDate = new Date();
                    TripsData db = new TripsData(MainActivity.this);
                    Trip[] trips = db.getTrips();
                    org.inspira.emag.shared.RPM[] rpmsViaje;
                    ThrottlePos[] throttlePosViaje;
                    Speed[] speedsViaje;
                    Location[] locationsViaje;
                    File f = new File(Environment.getExternalStorageDirectory(), "EMAG");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    f = new File(Environment.getExternalStorageDirectory() + "/EMAG", "Viajes");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    f = new File(Environment.getExternalStorageDirectory() + "/EMAG", "RPMs");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    f = new File(Environment.getExternalStorageDirectory() + "/EMAG", "Ubicaciones");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    f = new File(Environment.getExternalStorageDirectory() + "/EMAG", "Posiciones_Acel");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    f = new File(Environment.getExternalStorageDirectory() + "/EMAG", "Velocidades");
                    if (!f.exists()) {
                        f.mkdirs();
                    }
                    PrintWriter tripsWriter = new PrintWriter(new FileWriter( new File(DIR_VIAJE + "/" + ProveedorDeRecursos.obtenerFecha().split(" ")[0].replace("/","_") + "_" + VIAJE), true));
                    PrintWriter rpmWriter = new PrintWriter(new FileWriter(new File(DIR_RPM + "/" + ProveedorDeRecursos.obtenerFecha().split(" ")[0].replace("/","_") + "_" + RPM), true));
                    PrintWriter speedsWriter = new PrintWriter(new FileWriter(new File(DIR_VELOCIDAD + "/" + ProveedorDeRecursos.obtenerFecha().split(" ")[0].replace("/","_") + "_" + VELOCIDAD), true));
                    PrintWriter throttlePosWriter = new PrintWriter(new FileWriter(new File(DIR_THROTTLE_POS + "/" + ProveedorDeRecursos.obtenerFecha().split(" ")[0].replace("/","_") + "_" + THROTTLE_POS), true));
                    PrintWriter locationWriter = new PrintWriter(new FileWriter(new File(DIR_UBICACION + "/" + ProveedorDeRecursos.obtenerFecha().split(" ")[0].replace("/","_") + "_" + UBICACION), true));
                    String vehiculo = ProveedorDeRecursos.obtenerRecursoString(MainActivity.this, "vehiculo");
                    int idVehiculo = db.obtenerIdVehiculoFromNombre(vehiculo);
                    Log.d("Anttacker", "vehiculo: " + vehiculo + ", id: " + idVehiculo);
                    for (Trip trip : trips) {
                        tripsWriter.println(trip.getIdTrip() + "," + trip.getFechaInicio() + "," + trip.getFechaFin());
                        rpmWriter.println("Inicia viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        throttlePosWriter.println("Inicia viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        speedsWriter.println("Inicia viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        locationWriter.println("Inicia viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        rpmsViaje = db.getRPMsByTrip(trip.getIdTrip());
                        for (org.inspira.emag.shared.RPM rpm : rpmsViaje)
                            rpmWriter.println(idVehiculo + ", " + rpm.getIdValue() + ", " + rpm.getRpmValue() + ", " + rpm.getTimeStamp() + ", " + rpm.getIdTrip() + ", sincronizado? " + rpm.isCommited());
                        throttlePosViaje = db.getThrottlePosByTrip(trip.getIdTrip());
                        for (ThrottlePos throttlePos : throttlePosViaje)
                            throttlePosWriter.println(idVehiculo + ", " + throttlePos.getIdValue() + ", " + throttlePos.getThrottlePos() + ", " + throttlePos.getTimestamp() + ", " + throttlePos.getIdTrip() + ", sincronizado? " + throttlePos.isCommited());
                        speedsViaje = db.getSpeedsByTrip(trip.getIdTrip());
                        for (Speed speed : speedsViaje)
                            speedsWriter.println(idVehiculo + ", " + speed.getIdValue() + ", " + speed.getSpeed() + ", " + speed.getTimestamp() + ", " + speed.getIdTrip() + ", sincronizado? " + speed.isCommited());
                        locationsViaje = db.getLocationsByTrip(trip.getIdTrip());
                        for (Location location : locationsViaje)
                            locationWriter.println(idVehiculo + ", " + location.getIdValue() + ", " + location.getLatitud() + ", " + location.getLongitud() + ", " + location.getTimestamp() + ", " + location.getIdTrip() + ", sincronizado? " + location.isCommited());
                        rpmWriter.println("Termina viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        speedsWriter.println("Termina viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        throttlePosWriter.println("Termina viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        locationWriter.println("Termina viaje #" + trip.getIdTrip() + " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    }
                    tripsWriter.close();
                    rpmWriter.close();
                    throttlePosWriter.close();
                    speedsWriter.close();
                    locationWriter.close();
                    db.clearTables();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            makeSnackbar("Archivos creados");
                        }
                    });
                } catch (IOException ex) {
                    Log.d("From FileExportingSec", ex.getMessage());
                    ex.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            makeSnackbar("No hay datos para exportar");
                        }
                    });
                    Uploader up = new Uploader(new RawReading(ex.getMessage()));
                    up.setContext(MainActivity.this);
                    up.start();
                }
            }
        }.start();
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("backButtonCount", backButtonCount);
		outState.putBoolean("serverActionInProgress", serverActionInProgress);
        outState.putBoolean("serviceOn", serviceOn);
        outState.putString("button_label", buttonLabel.getText().toString());
        outState.putString("latitud", mLatitudeText.getText().toString());
        outState.putString("longitud", mLongitudeText.getText().toString());
        outState.putString("speed", speedText.getText().toString());
        outState.putString("rpm", rpmText.getText().toString());
        outState.putString("pda", pdaText.getText().toString());
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		backButtonCount = savedInstanceState.getInt("backButtonCount");
        buttonLabel.setText(savedInstanceState.getString("button_label"));
        mLatitudeText.setText(savedInstanceState.getString("latitud"));
        mLongitudeText.setText(savedInstanceState.getString("longitud"));
        speedText.setText(savedInstanceState.getString("speed"));
        rpmText.setText(savedInstanceState.getString("rpm"));
        pdaText.setText(savedInstanceState.getString("pda"));
		serverActionInProgress = savedInstanceState
				.getBoolean("serverActionInProgress");
        serviceOn = savedInstanceState.getBoolean("serviceOn");
        if(serviceOn){
            doBindService();
            clientMode.setBackgroundResource(R.drawable.on_button);
        }else {
            clientMode.setBackgroundResource(R.drawable.off);
        }
	}

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(serviceOn){
            doUnbindService();
            clientMode.setBackgroundResource(R.drawable.off);
        }
        if(serviceOnMock){
            doUnbindServiceMock();
            clientMode.setBackgroundResource(R.drawable.off);
        }
    }

	@Override
	public void onBackPressed() {
		if (backButtonCount < 2) {
			serverActionInProgress = false;
			backButtonCount++;
            if(serviceOn) {
                stopService(mService);
                clientMode.setBackgroundResource(R.drawable.off);
                serviceOn = false;
                buttonLabel.setText(getResources().getString(R.string.bienvinida));
                makeSnackbar("Lectura de datos detenida");
            }else{
                makeSnackbar("Presione una vez más para salir");
            }
        } else {
			super.onBackPressed();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
                case START_CLIENT_ACTION:
                    SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
                    editor.putString("device_addr", data.getStringExtra("device_addr"));
                    editor.apply();
                    clientMode.setBackgroundResource(R.drawable.on_button);
                    buttonLabel.setText(getResources().getString(R.string.detener_lectura_datos));
                    makeSnackbar("Lectura de datos iniciada");
                    manager.cancelDiscovery();
                    startService(mService);
                    doBindService();
                    break;
                case BluetoothManager.REQUEST_ENABLE_BT:
                    launchAction(START_CLIENT_ACTION);
                    break;
            }
		} else {
            if(requestCode == PREPARACION){
                finish();
            }else {
                clientMode.setBackgroundResource(R.drawable.off);
                serviceOn = false;
            }
		}
	}

    public void updateLocationData(String latitud, String longitud){
        TripsData db = new TripsData(this);
        Trip trip = db.getUnconcludedTrip();
        String date = ProveedorDeRecursos.obtenerFecha();
        if( trip != null ) {
            int locId = db.insertaUbicacion(latitud, longitud, date, trip.getIdTrip());
            Location cLoc = new Location(locId, latitud, longitud, date, trip.getIdTrip());
            Uploader up = new Uploader(cLoc);
            up.setContext(this);
            up.start();
        }
    }

    public void turnThingsOff(){
        clientMode.setBackgroundResource(R.drawable.off);
        serviceOn = false;
        buttonLabel.setText(getResources().getString(R.string.bienvinida));
    }

    public void setLatitudeText(String latitudeText){
        mLatitudeText.setText(latitudeText);
    }

    public void setLongitudeText(String longitudeText){
        mLongitudeText.setText(longitudeText);
    }

    public void setSpeedText(String speed){
        speedText.setText(speed);
    }

    public void setRpmText(String rpm){
        rpmText.setText(rpm);
    }

    public void setPdaText(String pda){
        pdaText.setText(pda);
    }

    private ObdMainService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mIsBound = true;
            serviceOn = true;
            mBoundService = ((ObdMainService.LocalBinder) service).getService();
            mBoundService.setActivity(MainActivity.this);
            Uploader up = new Uploader(new RawReading("Acabamos de ponerle algo aquí " + ProveedorDeRecursos.obtenerFecha()));
            up.setContext(MainActivity.this);
            up.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
        }
    };
    private boolean mIsBound;

    void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!mIsBound) {
            bindService(mService, mConnection,
                    Context.BIND_AUTO_CREATE);
            Log.d("DBZ","Bounded");
        }
    }

    public void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            Log.d("DBZ","unBounded");
        }
    }

    private void testingUnit(){
        new Thread(){
            @Override
            public void run(){
                try{
                    JSONObject json = new JSONObject();
                    json.put("action", 5);
                    json.put("fechaFin", ProveedorDeRecursos.obtenerFecha());
                    json.put("vehiculo", getSharedPreferences(OrganizarVehiculos.class.getName(), Context.MODE_PRIVATE).getString("vehiculo", "NaN"));
                    json.put("email", "jcc23@ipn.mx");
                    HttpURLConnection con = (HttpURLConnection) new URL(MainActivity.SERVER_URL).openConnection();
                    con.setDoOutput(true);
                    DataOutputStream salida = new DataOutputStream(con.getOutputStream());
                    salida.write(json.toString().getBytes());
                    salida.flush();
                    int length;
                    byte[] chunk = new byte[64];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataInputStream entrada = new DataInputStream(con.getInputStream());
                    while( (length = entrada.read(chunk)) != -1 )
                        baos.write(chunk,0,length);
                    Log.d("PHP Tester", baos.toString());
                    baos.close();
                    con.disconnect();
                    salida.close();
                    entrada.close();
                }catch(JSONException | IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
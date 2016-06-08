package org.inspira.emag.fragmentos;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.capiz.bluetooth.R;
import org.inspira.emag.actividades.OrganizarVehiculos;
import org.inspira.emag.actividades.ProveedorDeRecursos;
import org.inspira.emag.database.TripsData;
import org.inspira.emag.dialogos.ProveedorSnackBar;
import org.inspira.emag.networking.LoginConnection;
import org.inspira.emag.networking.ObtencionDeAutos;
import org.inspira.emag.seguridad.Hasher;
import org.inspira.emag.shared.User;
import org.inspira.emag.shared.Vehiculo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jcapiz on 11/03/16.
 */
public class SignIn extends Fragment {

    EditText user, password;
    Button start;
    String userString, pwString;
    SignUp.Acciones acciones;
    boolean performingConnection;

    public void setAcciones(SignUp.Acciones acciones) {
        this.acciones = acciones;
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.activity_login, parent, false);
        if(savedInstanceState == null)
            performingConnection = false;
        else performingConnection = savedInstanceState.getBoolean("performing_connection");
        user = (EditText)rootView.findViewById(R.id.usuario);
        password = (EditText)rootView.findViewById(R.id.pw);
        start = (Button)rootView.findViewById(R.id.iniciar);
        password.setTypeface(Typeface.createFromAsset(getActivity().getAssets(),"Roboto-Regular.ttf"));
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!performingConnection) {
                    ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected()) {
                        userString = user.getText().toString().trim();
                        pwString = password.getText().toString().trim();
                        if (!"".equals(userString) && !"".equals(pwString)) {
                            LoginConnection lc = new LoginConnection(new LoginConnection.OnConnectionAction() {
                                @Override
                                public void validationSucceded(JSONObject json) {
                                    obtencionDeAutos(json);
                                }

                                @Override
                                public void validationError() {
                                    cleanPass();
                                }
                            });
                            lc.setContext(SignIn.this);
                            lc.execute(userString, new Hasher().makeHashString(pwString));
                        } else {
                            ProveedorSnackBar
                                    .muestraBarraDeBocados(user, "Debe llenar los campos :)");
                        }
                    } else {
                        ProveedorSnackBar
                                .muestraBarraDeBocados(user, "Problemas de conexión :(");
                    }
                }else{
                    ProveedorSnackBar
                            .muestraBarraDeBocados(user, "Ya estamos conectando, espere =)");
                }
            }
        });
        rootView.findViewById(R.id.registrarse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SignUp signUp =  new SignUp();
                signUp.setAcciones(acciones);
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.preparacion_main_container, signUp)
                        .addToBackStack("login")
                        .commit();
            }
        });
        if(savedInstanceState != null){
            pwString = savedInstanceState.getString("pass");
            userString = savedInstanceState.getString("user");
            password.setText(pwString);
            user.setText(userString);
        }
        return rootView;
    }

    private void obtencionDeAutos(JSONObject json) {
        try {
            ObtencionDeAutos obt = new ObtencionDeAutos(json.getString("email"));
            obt.setAcciones(new RespuestaObtencionDeConvocatorias(json));
            obt.start();
        }catch(JSONException ignore){}
    }

    public void finishedPerformingConnection(){
        performingConnection = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        outState.putString("pass", pwString);
        outState.putString("user", userString);
        outState.putBoolean("performing_connection", performingConnection);
    }

    @Override
    public void onResume(){
        super.onResume();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle("Ingreso");
    }

    private void setUserInfo(JSONObject json){
        try{
            SharedPreferences.Editor editor = getActivity().getSharedPreferences(OrganizarVehiculos.class.getName(), Context.MODE_PRIVATE).edit();
            editor.putString("usuario", json.getString("nickname"));
            editor.putString("email", json.getString("email"));
            editor.apply();
            User usuario = new User();
            usuario.setEmail(json.getString("email"));
            usuario.setNickname(json.getString("nickname"));
            usuario.setDateOfBirth(json.getLong("fecha_de_nacimiento"));
            new TripsData(getContext()).addUser(usuario);
        }catch(JSONException e){
            e.printStackTrace();
            ProveedorSnackBar
                    .muestraBarraDeBocados(user, "Servicio temproalmente no disponible");
        }
    }

    private void cleanPass(){
        password.setText("");
        ProveedorSnackBar
                .muestraBarraDeBocados(password, "Error en las credenciales");
    }

    private class RespuestaObtencionDeConvocatorias implements ObtencionDeAutos.AccionesObtencionDeConvocatorias{

        private JSONObject usrInfo;

        public RespuestaObtencionDeConvocatorias(JSONObject usrInfo) {
            this.usrInfo = usrInfo;
        }

        @Override
        public void obtencionCorrecta(JSONObject json) {
            setUserInfo(usrInfo);
            setAutos(json);
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }

        @Override
        public void obtencionIncorrecta(final String mensaje) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ProveedorSnackBar
                            .muestraBarraDeBocados(user, mensaje);
                }
            });
        }
    }

    private void setAutos(JSONObject resp) {
        try {
            TripsData db = new TripsData(getContext());
            String email = getContext().getSharedPreferences(OrganizarVehiculos.class.getName(), Context.MODE_PRIVATE).getString("email", "NaN");
            JSONArray convocatorias = resp.getJSONArray("content");
            for(int i=0; i < convocatorias.length(); i++) {
                JSONObject json = convocatorias.getJSONObject(i);
                Vehiculo vehiculo = new Vehiculo();
                vehiculo.setEmail(email);
                vehiculo.setNombre(json.getString("nombre"));
                vehiculo.setIdVehiculo(json.getInt("idVehiculo"));
                db.addVehiculo(vehiculo);
                if(json.getInt("es_principal") > 0){
                    SharedPreferences.Editor editor = getActivity().getSharedPreferences(OrganizarVehiculos.class.getName(), Context.MODE_PRIVATE).edit();
                    editor.putString("vehiculo", vehiculo.getNombre());
                    editor.apply();
                }
            }
        }catch(JSONException e){
            e.printStackTrace();
        }
    }
}

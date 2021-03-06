package org.inspira.emag.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentResolver;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import org.capiz.bluetooth.R;
import org.inspira.emag.fragmentos.SignIn;
import org.inspira.emag.fragmentos.SignUp;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup);
        if(savedInstanceState == null)
            colocaFragmento();
    }

    private void colocaFragmento() {
        SignIn signIn = new SignIn();
        signIn.setAcciones(new SignUp.Acciones() {
            @Override
            public void onResume(String mensaje) {
                getSupportActionBar()
                        .setTitle(mensaje);
            }
        });
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.preparacion_main_container, signIn)
                .commit();
    }
}


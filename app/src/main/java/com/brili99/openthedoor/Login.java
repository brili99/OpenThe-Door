package com.brili99.openthedoor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class Login extends AppCompatActivity {
    AppCompatEditText etEmail, etPassword;
    Button btnLogin;
    public RequestQueue queue;
    String urlLogin = "http://172.17.1.12/musium/API/app_login.php";
//    String urlLogin = "http://192.168.2.233/musium/API/app_login.php";

    String urlVerifToken = "http://172.17.1.12/musium/API/verify_token.php";
//    String urlVerifToken = "http://192.168.2.233/musium/API/verify_token.php";
    private SweetAlertDialog pDialog;
    private String DIRECTORY = null;
    private String mainKeyAlias;
    private boolean tokenPass = false;
    private String androidId;
    private String filePath;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = (AppCompatEditText) findViewById(R.id.etEmail);
        etPassword = (AppCompatEditText) findViewById(R.id.etPassword);
        btnLogin = (Button) findViewById(R.id.btnLogin);
        queue = Volley.newRequestQueue(this);
        DIRECTORY = getFilesDir().getAbsolutePath();
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        filePath = DIRECTORY + "/auth";

        File fAuth = new File(filePath);
        if (fAuth.exists()) {
            try {
                token = getAuthToken();
                if (verifikasiToken(token)) {
                    cekServerToken(token);
                }
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Validate form
                boolean formEmpty = false;
                if (TextUtils.isEmpty(etEmail.getText())) {
                    etEmail.setError("Email is required!");
                    formEmpty = true;
                } else {
                    if (!isEmailValid(etEmail.getText().toString())) {
                        etEmail.setError("Email not valid!");
                        formEmpty = true;
                    }
                }
                if (TextUtils.isEmpty(etPassword.getText())) {
                    etPassword.setError("Password is required!");
                    formEmpty = true;
                }

                if (!formEmpty) {
                    doLogin(etEmail.getText().toString(), etPassword.getText().toString());
                }
            }
        });
    }

    public static boolean isEmailValid(String email) {
        String expression = "^[\\w\\.-]+@([\\w\\-]+\\.)+[A-Z]{2,4}$";
        Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    private void doLogin(String email, String password) {
        pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#0d6efd"));
        pDialog.setTitleText("Loading");
        pDialog.setCancelable(false);
        pDialog.show();
        StringRequest postRequest = new StringRequest(Request.Method.POST, urlLogin,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        pDialog.dismiss();
                        Log.d("Response", response);

                        if (verifikasiToken(response)) {
                            tokenPass = true;
                            token = response;
                        } else {
                            showSweetAlert("Error", response, SweetAlertDialog.ERROR_TYPE);
                        }

                        Log.d("filePath", filePath);
                        File fdelete = new File(filePath);
                        if (fdelete.exists() && tokenPass) {
                            if (fdelete.delete()) {
                                try {
                                    saveAuthToken(response.toString());
//                                    showSweetAlert("Sukses", "Sukses menyimpan token", SweetAlertDialog.SUCCESS_TYPE);
                                    startActivity(new Intent(Login.this, MainActivity.class));
                                } catch (GeneralSecurityException e) {
                                    e.printStackTrace();
                                    showSweetAlert("Error", "GeneralSecurityException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    showSweetAlert("Error", "IOException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
                                }
                            } else {
                                showSweetAlert("Error", "Gagal menghapus otentikasi lama", SweetAlertDialog.ERROR_TYPE);
                            }
                        } else {
                            try {
                                saveAuthToken(response.toString());
//                                showSweetAlert("Sukses", "Sukses menyimpan token", SweetAlertDialog.SUCCESS_TYPE);
                                startActivity(new Intent(Login.this, MainActivity.class));
                            } catch (GeneralSecurityException e) {
                                e.printStackTrace();
                                showSweetAlert("Error", "GeneralSecurityException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
                            } catch (IOException e) {
                                e.printStackTrace();
                                showSweetAlert("Error", "IOException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
                            }
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
//                        pDialog.dismiss();
//                        // error
//                        Log.d("Error.Response", error.toString());
//                        showSweetAlert("Error", error.toString(), SweetAlertDialog.ERROR_TYPE);
                        pDialog.dismiss();
                        // error
                        Log.d("Error.Response", error.toString());
                        if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                            showSweetAlert("Connection timeout", "Mungkin anda salah jaringan.", SweetAlertDialog.ERROR_TYPE);
                        } else {
                            showSweetAlert("Error", error.toString(), SweetAlertDialog.ERROR_TYPE);
                        }
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("android_id", androidId);
                params.put("email", email);
                params.put("password", password);
                return params;
            }
        };

//        postRequest.setRetryPolicy(new DefaultRetryPolicy(
//                20000,
//                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
//                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(postRequest);
    }

    private void showSweetAlert(String title, String desc, int icon) {
        pDialog = new SweetAlertDialog(this, icon);
        pDialog.setTitleText(title)
                .setContentText(desc)
                .show();
    }

    private void saveAuthToken(String token) throws GeneralSecurityException, IOException {
        Context context = getApplicationContext();

        // Although you can define your own key generation parameter specification, it's
        // recommended that you use the value specified here.
        KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
        mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);

        // Create a file with this name, or replace an entire existing file
        // that has the same name. Note that you cannot append to an existing file,
        // and the file name cannot contain path separators.
        String fileToWrite = "auth";
        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                new File(DIRECTORY, fileToWrite),
                context,
                mainKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        byte[] fileContent = token.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = encryptedFile.openFileOutput();
        outputStream.write(fileContent);
        outputStream.flush();
        outputStream.close();
    }

    private String getAuthToken() throws GeneralSecurityException, IOException {
        Context context = getApplicationContext();

        // Although you can define your own key generation parameter specification, it's
        // recommended that you use the value specified here.
        KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
        String mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);

        String fileToRead = "auth";
        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                new File(DIRECTORY, fileToRead),
                context,
                mainKeyAlias,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        InputStream inputStream = encryptedFile.openFileInput();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int nextByte = inputStream.read();
        while (nextByte != -1) {
            byteArrayOutputStream.write(nextByte);
            nextByte = inputStream.read();
        }

//        byte[] plaintext = byteArrayOutputStream.toByteArray();
        return byteArrayOutputStream.toString();
    }

    private boolean verifikasiToken(String _token) {
        return _token.length() == 40;
    }

    private void cekServerToken(String _token){
        pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#0d6efd"));
        pDialog.setTitleText("Loading");
        pDialog.setCancelable(false);
        pDialog.show();
        StringRequest postRequest = new StringRequest(Request.Method.POST, urlVerifToken,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        pDialog.dismiss();
                        if (response.equals("success")) {
                            startActivity(new Intent(Login.this, MainActivity.class));
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
//                        pDialog.dismiss();
//                        // error
//                        Log.d("Error.Response", error.toString());
//                        showSweetAlert("Error", error.toString(), SweetAlertDialog.ERROR_TYPE);
                        pDialog.dismiss();
                        // error
                        Log.d("Error.Response", error.toString());
                        if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                            showSweetAlert("Connection timeout", "Mungkin anda salah jaringan.", SweetAlertDialog.ERROR_TYPE);
                        }
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("android_id", androidId);
                params.put("token", _token);
                return params;
            }
        };
        queue.add(postRequest);
    }

//    private void editAuthToken(String token) throws GeneralSecurityException, IOException {
//        String sharedPrefsFile = DIRECTORY;
//        SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
//                sharedPrefsFile,
//                mainKeyAlias,
//                getApplicationContext(),
//                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
//                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
//        );
//
//        SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
//        // Edit the user's shared preferences...
//        sharedPrefsEditor.apply();
//    }
}
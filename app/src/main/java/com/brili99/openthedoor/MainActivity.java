package com.brili99.openthedoor;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.ClientError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.Result;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class MainActivity extends AppCompatActivity {
    private CodeScanner mCodeScanner;
    public static final int REQUEST_AUDIO_PERMISSION_CODE = 1;
    private SweetAlertDialog pDialog;
    //    private TextView btnManualMasuk;
    private String token;
    private TextInputEditText inputIdManual;
    public String androidId;
    public RequestQueue queue;
    private String DIRECTORY = null;
    public String URL = "http://172.17.1.12/musium/API/qr_scan.php";
//    public String URL = "http://192.168.2.233/musium/API/qr_scan.php";
//    public String URL = "http://192.168.2.233/OpenThe_Door/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CodeScannerView scannerView = findViewById(R.id.scanner_view);
//        btnManualMasuk = (TextView) findViewById(R.id.btnManualMasuk);
//        inputIdManual = (TextInputEditText) findViewById(R.id.inputIdManual);

        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DIRECTORY = getFilesDir().getAbsolutePath();
        queue = Volley.newRequestQueue(this);
        if (CheckPermissions()) {
            mCodeScanner = new CodeScanner(this, scannerView);
            mCodeScanner.setDecodeCallback(new DecodeCallback() {
                @Override
                public void onDecoded(@NonNull final Result result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(MainActivity.this, result.getText(), Toast.LENGTH_SHORT).show();
                            sendVoiceToServer(result.getText());
//                            showSweetAlert("Code",result.getText(),SweetAlertDialog.NORMAL_TYPE);
                        }
                    });
                }
            });
            scannerView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mCodeScanner.startPreview();
                }
            });
        } else {
            RequestPermissions();
        }

        try {
            token = getAuthToken();
//            showSweetAlert("Sukses", "Token: " + token, SweetAlertDialog.SUCCESS_TYPE);
            showSweetAlert("Sukses", "Login sukses", SweetAlertDialog.SUCCESS_TYPE);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            showSweetAlert("Error", "GeneralSecurityException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
            showSweetAlert("Error", "IOException: " + e.toString(), SweetAlertDialog.ERROR_TYPE);
        }

//        btnManualMasuk.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (inputIdManual.getText().toString().equals("")) {
//                    showSweetAlert("Error", "Harap masukan id pintu.", SweetAlertDialog.ERROR_TYPE);
//                } else {
//                    sendVoiceToServer(inputIdManual.getText().toString());
//                }
//            }
//        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCodeScanner.startPreview();
    }

    @Override
    protected void onPause() {
        mCodeScanner.releaseResources();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // this method is called when user will
        // grant the permission for audio recording.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_AUDIO_PERMISSION_CODE:
                if (grantResults.length > 0) {
                    boolean permissionToCamera = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (permissionToCamera) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "This app need camera permission", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    public boolean CheckPermissions() {
        // this method is used to check permission
        int result = ContextCompat.checkSelfPermission(getApplicationContext(), CAMERA);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void RequestPermissions() {
        // this method is used to request the
        // permission for audio recording and storage.
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{CAMERA}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    private void showSweetAlert(String title, String desc, int icon) {
        pDialog = new SweetAlertDialog(this, icon);
        pDialog.setTitleText(title)
                .setContentText(desc)
                .show();
    }

    private void sendVoiceToServer(String id_qr) {
        pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
        pDialog.getProgressHelper().setBarColor(Color.parseColor("#0d6efd"));
        pDialog.setTitleText("Loading");
        pDialog.setCancelable(false);
        pDialog.show();
//        try {
//            JSONObject jsonBody = new JSONObject();
//            jsonBody.put("androidId", androidId);
//            jsonBody.put("pintu_id", id_qr);
//            final String requestBody = jsonBody.toString();
//
//            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
//                @Override
//                public void onResponse(String response) {
//                    Log.i("VOLLEY", response);
////                    web_response.setText(response);
//                    response = response.trim();
//                    pDialog.dismiss();
//                    if (response.equals("200")) {
//                        showSweetAlert("Sukses", "Berhasil menghubungi server", SweetAlertDialog.SUCCESS_TYPE);
//                    } else if (response.equals("500")) {
//                        showSweetAlert("Error", "Error pada server", SweetAlertDialog.ERROR_TYPE);
//                    }
//                }
//            }, new Response.ErrorListener() {
//                @Override
//                public void onErrorResponse(VolleyError error) {
//                    Log.e("VOLLEY", error.toString());
//                    pDialog.dismiss();
//                    if (error instanceof TimeoutError || error instanceof NoConnectionError) {
//                        showSweetAlert("Connection timeout", "Mungkin anda salah jaringan.", SweetAlertDialog.ERROR_TYPE);
//                    } else {
//                        showSweetAlert("Error", error.toString(), SweetAlertDialog.ERROR_TYPE);
//                    }
//                }
//            }) {
//                @Override
//                public String getBodyContentType() {
//                    return "application/json; charset=utf-8";
//                }
//
//                @Override
//                public byte[] getBody() throws AuthFailureError {
//                    try {
//                        return requestBody == null ? null : requestBody.getBytes("utf-8");
//                    } catch (UnsupportedEncodingException uee) {
//                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
//                        return null;
//                    }
//                }
//
//                @Override
//                protected Response<String> parseNetworkResponse(NetworkResponse response) {
//                    String responseString = "";
//                    if (response != null) {
//                        responseString = String.valueOf(response.statusCode);
////                        showSweetAlert("Sukses",responseString, SweetAlertDialog.SUCCESS_TYPE);
//                        // can get more details such as response.headers
//                    }
//                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
//                }
//            };
//
//            // Set timout request to 20 second
//            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
//                    20000,
//                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
//                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
//
//            queue.add(stringRequest);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }

        StringRequest postRequest = new StringRequest(Request.Method.POST, URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        pDialog.dismiss();
                        Log.d("Response", response);
                        if (response.equals("success")) {
                            showSweetAlert("Sukses", "Berhasil menghubungi server", SweetAlertDialog.SUCCESS_TYPE);
                        } else {
                            showSweetAlert("Error", response, SweetAlertDialog.ERROR_TYPE);
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        pDialog.dismiss();
                        // error
                        Log.d("Error.Response", error.toString());
                        if (error instanceof TimeoutError || error instanceof NoConnectionError) {
                            showSweetAlert("Connection timeout", "Mungkin anda salah jaringan.", SweetAlertDialog.ERROR_TYPE);
                        } else if (error instanceof ClientError || error instanceof AuthFailureError) {
                            finish();
//                            startActivity(new Intent(MainActivity.this, Login.class));
                        } if (error instanceof ServerError) {
                            showSweetAlert("Server error [500]", "Sepertinya ada yang salah dengan server, mohon hubungi developer.", SweetAlertDialog.ERROR_TYPE);
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
                params.put("pintu_id", id_qr);
                params.put("token", token);
                return params;
            }
        };
        queue.add(postRequest);
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
}
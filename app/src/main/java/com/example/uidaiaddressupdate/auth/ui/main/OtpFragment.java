package com.example.uidaiaddressupdate.auth.ui.main;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uidaiaddressupdate.Constants;
import com.example.uidaiaddressupdate.MainActivity;
import com.example.uidaiaddressupdate.R;
import com.example.uidaiaddressupdate.service.auth.AuthApiEndpointInterface;
import com.example.uidaiaddressupdate.service.auth.AuthapiService;
import com.example.uidaiaddressupdate.service.auth.model.Authotprequest;
import com.example.uidaiaddressupdate.service.auth.model.Authotpresponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class OtpFragment extends Fragment {

    private EditText otp;
    private TextView resendOtp;
    private Button submit;
    private String FCMtoken = null;
    private ProgressBar progressBar;
    public OtpFragment() {
        // Required empty public constructor
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        String transactionId = getArguments().getString("transactionId");
        String aadharNumber = getArguments().getString("aadharNumber");

        View view = inflater.inflate(R.layout.fragment_otp, container, false);
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        otp = (EditText) view.findViewById(R.id.otp_et_enter_otp);
        resendOtp = (TextView) view.findViewById(R.id.otp_resend_otp);
        submit = (Button) view.findViewById(R.id.otp_verify_button);

        getFCMRegistrationToken();
        submit.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {

                String otp_text = otp.getText().toString();
                if(otp_text.length() != 6){
                    Toast.makeText(getContext(), "Enter correct OTP", Toast.LENGTH_SHORT).show();
                    otp.setError("Enter Correct OTP");
                    otp.requestFocus();
                    return;
                }

//                progressBar.setVisibility(View.VISIBLE);
                progressDialog.setMessage("Verifying OTP");
                progressDialog.show();
                AuthApiEndpointInterface apiServie = AuthapiService.getApiInstance();
                String pubkeyString = "";
                try {
                    pubkeyString = generateEncryptionKeys();
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }


                Log.d("FCM Token:",FCMtoken);
                Authotprequest authotprequest = new Authotprequest(transactionId,otp.getText().toString(),FCMtoken,pubkeyString,aadharNumber);
                apiServie.authenticate(authotprequest).enqueue(new Callback<Authotpresponse>() {
                    @Override
                    public void onResponse(Call<Authotpresponse> call, Response<Authotpresponse> response) {
                        //progressBar.setVisibility(View.GONE);
                        progressDialog.dismiss();
                        switch(response.code()){
                            case 200:
                                try {
                                    KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
                                    String mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);
                                    SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                                            Constants.SHARED_PREFERENCES_FILE,
                                            mainKeyAlias,
                                            getContext(),
                                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                    );
                                    SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
                                    Log.d("Mohan",response.body().toString());
                                    sharedPrefsEditor.putString(Constants.KEY_AUTH_TOKEN,response.body().getAuthToken());
                                    sharedPrefsEditor.putString(Constants.KEY_UID_TOKEN,response.body().getUidToken());
                                    sharedPrefsEditor.putString(Constants.KEY_SHAREABLE_CODE,response.body().getShareableCode());
                                    sharedPrefsEditor.putString(Constants.KEY_AADHAR_NUMBER,aadharNumber);

                                    sharedPrefsEditor.commit();
                                } catch (GeneralSecurityException e) {
                                    e.printStackTrace();
                                    //App band karo
                                } catch (IOException e) {
                                    //App band karo
                                    e.printStackTrace();
                                }

                                SendToHome();
                                break;

                            case 400:
                                Toast.makeText(getActivity(),"Invalid request parameters",Toast.LENGTH_SHORT).show();
                                break;


                            case 403:
                            case 501:
                                Toast.makeText(getActivity(),"Invalid OTP",Toast.LENGTH_SHORT).show();
                                otp.getText().clear();
                                break;

                            case 503:
                                Toast.makeText(getActivity(),"Maximum trials for OTP check reached, please request OTP again",Toast.LENGTH_SHORT).show();
                                getActivity().getSupportFragmentManager().popBackStackImmediate();
                                break;

                            default:
                                Toast.makeText(getActivity(),"Error code: "+String.valueOf(response.code()),Toast.LENGTH_SHORT).show();
                                break;
                        }

                    }

                    @Override
                    public void onFailure(Call<Authotpresponse> call, Throwable t) {
                        //progressBar.setVisibility(View.GONE);
                        progressDialog.dismiss();
                        Toast.makeText(getContext(), "Error : " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        return view;
    }

    private void SendToHome(){
        getActivity().finish();
        Intent intent = new Intent(getActivity(), MainActivity.class);
        startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String generateEncryptionKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec
                .Builder("aadharkeys", KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA1)
                .build();
        kpg.initialize(keyGenParameterSpec);

        KeyPair kp = kpg.generateKeyPair();
        String publicKeyString = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.DEFAULT);
        Log.d("KeyTest",publicKeyString);

        return publicKeyString;
    }

    private void getFCMRegistrationToken(){
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if(task.isSuccessful()){
                    FCMtoken = task.getResult();
                    Log.d("FCMService", "Token " + FCMtoken);
                }
            }
        });
    }
}
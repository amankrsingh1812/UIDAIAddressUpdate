package com.example.uidaiaddressupdate.landlord;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uidaiaddressupdate.Constants;
import com.example.uidaiaddressupdate.EncryptionUtils;
import com.example.uidaiaddressupdate.R;
import com.example.uidaiaddressupdate.SharedPrefHelper;
import com.example.uidaiaddressupdate.Util;
import com.example.uidaiaddressupdate.XMLUtils;
import com.example.uidaiaddressupdate.database.LandlordTransactions;
import com.example.uidaiaddressupdate.database.TransactionDatabase;
import com.example.uidaiaddressupdate.service.offlineekyc.OfflineEKYCService;
import com.example.uidaiaddressupdate.service.offlineekyc.model.ekycoffline.OfflineEkycXMLResponse;
import com.example.uidaiaddressupdate.service.offlineekyc.model.otp.OtpResponse;
import com.example.uidaiaddressupdate.service.server.ServerApiService;
import com.example.uidaiaddressupdate.service.server.model.getpublickey.Publickeyrequest;
import com.example.uidaiaddressupdate.service.server.model.getpublickey.Publickeyresponse;
import com.example.uidaiaddressupdate.service.server.model.sendekyc.Sendekycresponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class LandlordOtpPage extends Fragment {

    private EditText otp_edit_text;
    private TextView resend_otp;
    private Button submit_otp;
    private String otpTxnId;
    private View view;
    private String receiverShareCode;
    private String transactionId;

    public LandlordOtpPage() {
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
        view =  inflater.inflate(R.layout.fragment_landlord_otp_page, container, false);

        String captchaText = getArguments().getString("captchaText");
        String captchaTxnId = getArguments().getString("captchaTxnId");
        transactionId = getArguments().getString(Constants.KEY_TRANSACTION_ID);
        receiverShareCode = getArguments().getString(Constants.KEY_RECEIVER_SHARECODE_ID);
        sendOTP(captchaText,captchaTxnId);


        otp_edit_text = (EditText) view.findViewById(R.id.landlord_otp_et_enter_otp);
        resend_otp = (TextView) view.findViewById(R.id.landlord_otp_resend_otp);
        submit_otp = (Button) view.findViewById(R.id.landlord_otp_verify_button);

        resend_otp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendOTP(captchaText,captchaTxnId);
            }
        });

        submit_otp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("eKYC",otp_edit_text.getText().toString());
                Log.d("eKYC",otpTxnId);
                String passcode = Util.getRandomString();
                OfflineEKYCService.makeOfflineEKYCCall(SharedPrefHelper.getUidToken(getContext()),otp_edit_text.getText().toString(),otpTxnId,passcode).enqueue(new Callback<OfflineEkycXMLResponse>() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void onResponse(Call<OfflineEkycXMLResponse> call, Response<OfflineEkycXMLResponse> response) {
                        String filename = response.body().getFileName();
                        String eKyc = response.body().geteKycXML();
                        Log.d("eKYC", response.body().geteKycXML());

//                        try {
//                            Log.d("eKYC decrrypted", XMLUtils.getKYCxmlFromZip(response.body().geteKycXML(), passcode));
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }

                        encryptPasscodeAndSendEkyc(filename,passcode,eKyc);

                    }

                    @Override
                    public void onFailure(Call<OfflineEkycXMLResponse> call, Throwable t) {
                        t.printStackTrace();
                    }
                });
            }
        });

        return view;
    }

    private void sendToLandlordAddressApprovedAckPage(){
        Navigation.findNavController(view).navigate(R.id.action_landlordOtpPage_to_landlordAddressApprovedAck);
    }

    private void encryptPasscodeAndSendEkyc(String filename,String passcode, String eKyc){
        ServerApiService.getApiInstance().getPublicKey(new Publickeyrequest(SharedPrefHelper.getUidToken(getContext()),SharedPrefHelper.getAuthToken(getContext()),receiverShareCode)).enqueue(new Callback<Publickeyresponse>() {
            @Override
            public void onResponse(Call<Publickeyresponse> call, Response<Publickeyresponse> response) {
                String receiverPublicKey = response.body().getPublicKey();
                Log.d("LandlordOtpPage", "Public Key: "+receiverPublicKey);

                String encryptedPasscode = null;
                try {
                    encryptedPasscode = EncryptionUtils.encryptMessage(receiverPublicKey,passcode);
                    sendEkyc(filename,encryptedPasscode,eKyc);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<Publickeyresponse> call, Throwable t) {

            }
        });
    }

    private void sendEkyc(String filename,String passcode, String eKyc){
        ServerApiService.sendEkyc(transactionId,filename,passcode,eKyc).enqueue(new Callback<Sendekycresponse>() {
            @Override
            public void onResponse(Call<Sendekycresponse> call, Response<Sendekycresponse> response) {
                Log.d("Mohan","Ekyc Uploaded");

                //Update in Client Side DB
                LandlordTransactions curTransaction = TransactionDatabase.getInstance(getContext()).landlordTransactionsDao().getTransaction(transactionId);
                curTransaction.setTransactionStatus("accepted");
                TransactionDatabase.getInstance(getContext()).landlordTransactionsDao().insertTransaction(curTransaction);

                sendToLandlordAddressApprovedAckPage();
            }
            @Override
            public void onFailure(Call<Sendekycresponse> call, Throwable t) {
                Toast.makeText(getContext(), "Error : " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
//9999527333847
    private void sendOTP(String captchaText, String captchaTxnId){
        OfflineEKYCService.makeOTPCall(SharedPrefHelper.getUidToken(getContext()),captchaTxnId,captchaText).enqueue(new Callback<OtpResponse>() {
            @Override
            public void onResponse(Call<OtpResponse> call, Response<OtpResponse> response) {
                Log.d("eKYC", response.body().getMessage());
                otpTxnId = response.body().getTxnId();
            }

            @Override
            public void onFailure(Call<OtpResponse> call, Throwable t) {
                t.printStackTrace();
                //End App
            }
        });
    }
}
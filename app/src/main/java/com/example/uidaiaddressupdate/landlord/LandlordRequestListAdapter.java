package com.example.uidaiaddressupdate.landlord;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.uidaiaddressupdate.R;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

public class LandlordRequestListAdapter extends RecyclerView.Adapter<LandlordRequestListAdapter.ViewHolder> {

    private List<SingleLandlordRequestModel> AddressRequestsList;
    private LandlordRequests landlordRequestsInterface;

    public LandlordRequestListAdapter(List<SingleLandlordRequestModel> addressRequestsList, LandlordRequests landlordRequestsInterface) {
        AddressRequestsList = addressRequestsList;
        this.landlordRequestsInterface = landlordRequestsInterface;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.single_address_request_layout,parent,false));
        return view;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SingleLandlordRequestModel requestModel  = AddressRequestsList.get(position);
        holder.name.setText(requestModel.getUsername());
        holder.phone.setText(requestModel.getPhonenumber());
        holder.approve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                landlordRequestsInterface.GotToCaptchaPage(requestModel.getTransactionId());
            }
        });

        holder.decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                landlordRequestsInterface.HandleRequestDeclined(requestModel.getTransactionId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return AddressRequestsList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView name,phone;
        private ImageView userImage;
        private AppCompatButton approve,decline;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            name = (TextView) itemView.findViewById(R.id.single_name);
            phone = (TextView) itemView.findViewById(R.id.single_phone);
            userImage = (ImageView) itemView.findViewById(R.id.single_image);
            approve = (AppCompatButton) itemView.findViewById(R.id.single_approve);
            decline = (AppCompatButton) itemView.findViewById(R.id.single_reject);
        }
    }
}
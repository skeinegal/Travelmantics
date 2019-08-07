package com.example.travelmantics;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.util.Objects;

public class DealActivity extends AppCompatActivity {
    public static final String TRAVEL_DEALS_PATH = "traveldeals";
    public static final int INSERT_PICTURE_REQ_CODE = 42;
    private static final String TAG = "DealActivity";
    EditText txtTitle;
    EditText txtDescription;
    EditText txtPrice;
    TravelDeal deal;
    ImageView mImageView;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    private Button mButtonImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal);

        mImageView = findViewById(R.id.image);
        mFirebaseDatabase = FirebaseUtil.sFirebaseDatabase;
        mDatabaseReference = FirebaseUtil.sDatabaseReference;

        txtTitle = findViewById(R.id.txtTitle);
        txtDescription = findViewById(R.id.txtDescription);
        txtPrice = findViewById(R.id.txtPrice);

        Intent intent = getIntent();
        TravelDeal deal = (TravelDeal) intent.getSerializableExtra("deal");
        if (deal == null) {
            deal = new TravelDeal();
        }

        this.deal = deal;
        txtTitle.setText(deal.getTitle());
        txtDescription.setText(deal.getDescription());
        txtPrice.setText(deal.getPrice());

        mButtonImage = findViewById(R.id.btnImage);
        mButtonImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent,
                        "Insert Picture"), INSERT_PICTURE_REQ_CODE);
            }
        });

        showImage(deal.getImageUrl());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_menu, menu);

        MenuItem deleteItem = menu.findItem(R.id.action_delete_deal);
        MenuItem saveItem = menu.findItem(R.id.action_save_menu);

        if (FirebaseUtil.sIsUserAdmin) {
            deleteItem.setVisible(true);
            saveItem.setVisible(true);
            enableEditText(true);
            mButtonImage.setEnabled(true);
        } else {
            deleteItem.setVisible(false);
            saveItem.setVisible(false);
            enableEditText(false);
            mButtonImage.setEnabled(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_save_menu:
                saveDeal();
                Toast.makeText(this, "Deal Saved", Toast.LENGTH_LONG).show();
                cleanEditText();
                backToList();
                return true;
            case R.id.action_delete_deal:
                deleteDeal();
                Toast.makeText(this, "Deal Deleted", Toast.LENGTH_LONG).show();
                backToList();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void cleanEditText() {
        txtTitle.setText("");
        txtDescription.setText("");
        txtPrice.setText("");
        txtTitle.requestFocus();
    }

    private void saveDeal() {
        deal.setTitle(txtTitle.getText().toString());
        deal.setDescription(txtDescription.getText().toString());
        deal.setPrice(txtPrice.getText().toString());

        if (deal.getId() == null) {
            mDatabaseReference.push().setValue(deal);
        } else {
            mDatabaseReference.child(deal.getId()).setValue(deal);
        }
    }

    private void deleteDeal() {
        if (deal == null) {
            Toast.makeText(this, "Error. Deal does not exist", Toast.LENGTH_LONG).show();
            return;
        }
        mDatabaseReference.child(deal.getId()).removeValue();

        if (deal.getImageName() != null && !deal.getImageName().isEmpty()) {
            StorageReference picRef = FirebaseUtil.sFirebaseStorage.getReference().
                    child(deal.getImageName());
            picRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(DealActivity.this, "Delete image Successful",
                            Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(DealActivity.this, "Delete image failed",
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "onFailure: -------------" + e.getMessage());
                }
            });
        }
    }

    private void backToList() {
        startActivity(new Intent(this, ListActivity.class));
    }

    private void enableEditText(boolean isEnabled) {
        txtDescription.setEnabled(isEnabled);
        txtTitle.setEnabled(isEnabled);
        txtPrice.setEnabled(isEnabled);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INSERT_PICTURE_REQ_CODE && resultCode == RESULT_OK) {
            assert data != null;
            Uri imageUri = data.getData();
            assert imageUri != null;
            final StorageReference reference = FirebaseUtil.sStorageReference.
                    child(Objects.requireNonNull(imageUri.getLastPathSegment()));

            final UploadTask uploadTask = reference.putFile(imageUri);

            uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot,
                    Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    // Continue with the task to get the download URL
                    return reference.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        String downloadUri = Objects.requireNonNull(task.getResult()).toString();
                        String fileName = uploadTask.getSnapshot().getStorage().getPath();
                        Log.e(TAG, "onComplete: -----------------" + fileName);
                        deal.setImageUrl(downloadUri);
                        deal.setImageName(fileName);
                        showImage(downloadUri);
                    } else {
                        Toast.makeText(DealActivity.this, "", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showImage(String url) {
        if (url != null && !url.isEmpty()) {
            int width = Resources.getSystem().getDisplayMetrics().widthPixels;
            Picasso.get()
                    .load(url)
                    .resize(width, width * 2 / 3)
                    .centerCrop()
                    .into(mImageView);
        }
    }
}
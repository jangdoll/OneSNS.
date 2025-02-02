package com.example.onesns.chatProfile;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.example.onesns.R;
import com.example.onesns.profileSetting.ProfileActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hbb20.CountryCodePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

public class ChatProfileActivity extends AppCompatActivity {

    private Toolbar mToolbar;
    private Button sendFriendRequest_Button, declineFriendRequest_Button;
    private TextView profileName, profileNickname, profileStatus, profileGender, go_my_profile;
    private CountryCodePicker profileCountry;
    private ImageView profileImage, verified_icon;

    private DatabaseReference userDatabaseReference;

    private DatabaseReference friendRequestReference;
    private FirebaseAuth mAuth;
    private String CURRENT_STATE;

    public String receiver_userID; // Visited profile's id
    public String senderID; // Owner ID

    private DatabaseReference friendsDatabaseReference;
    private DatabaseReference notificationDatabaseReference;


    // for glide error -> You cannot start a load for a destroyed activity
    public RequestManager mGlideRequestManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_activity_profile);

        // 상태표시줄 색상 변경
        View view = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 23 버전 이상일 때 상태바 하얀 색상, 회색 아이콘
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(Color.parseColor("#f2f2f2"));
        } else if (Build.VERSION.SDK_INT >= 21) {
            // 21 버전 이상일 때 상태바 검은 색상, 흰색 아이콘
            getWindow().setStatusBarColor(Color.BLACK);
        }

        // for glide error -> You cannot start a load for a destroyed activity
        mGlideRequestManager = Glide.with(getApplicationContext());

        userDatabaseReference = FirebaseDatabase.getInstance().getReference().child("users");
        friendRequestReference = FirebaseDatabase.getInstance().getReference().child("friend_requests");
        friendRequestReference.keepSynced(true); // for offline

        mAuth = FirebaseAuth.getInstance();
        senderID = mAuth.getCurrentUser().getUid(); // GET SENDER ID

        friendsDatabaseReference = FirebaseDatabase.getInstance().getReference().child("friends");
        friendsDatabaseReference.keepSynced(true); // for offline

        notificationDatabaseReference = FirebaseDatabase.getInstance().getReference().child("notifications");
        notificationDatabaseReference.keepSynced(true); // for offline

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // back on previous activity
        mToolbar.setNavigationOnClickListener(v -> {
            Log.d("tag", "onClick : navigating back to back activity ");
            finish();
        });

        receiver_userID = getIntent().getExtras().get("visitUserId").toString();

        sendFriendRequest_Button = findViewById(R.id.visitUserFrndRqstSendButton);
        declineFriendRequest_Button = findViewById(R.id.visitUserFrndRqstDeclineButton);
        profileName = findViewById(R.id.visitUserProfileName);
        profileStatus = findViewById(R.id.visitUserProfileStatus);
        profileCountry = findViewById(R.id.visitUserProfileCountry);
        profileGender = findViewById(R.id.visitUserProfileGender);
        verified_icon = findViewById(R.id.visit_verified_icon);
        profileImage = findViewById(R.id.visit_user_profile_image);
        go_my_profile = findViewById(R.id.go_my_profile);

        // 나라 수정불가
        profileCountry.setFocusable(false);
        profileCountry.setClickable(false);


        verified_icon.setVisibility(View.INVISIBLE);

        CURRENT_STATE = "not_friends";

        /**
         * Load every single users data
         */
        userDatabaseReference.child(receiver_userID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String name = dataSnapshot.child("user_name").getValue().toString();
                String nickname = dataSnapshot.child("user_nickname").getValue().toString();
                String status = dataSnapshot.child("user_status").getValue().toString();
                String country = dataSnapshot.child("user_country").getValue().toString();
                String gender = dataSnapshot.child("user_gender").getValue().toString();
                String image = dataSnapshot.child("user_image").getValue().toString();
                String verified = dataSnapshot.child("verified").getValue().toString();

                profileName.setText(name);
//                profileNickname.setText(nickname);
                profileStatus.setText(status);
                profileCountry.setCountryForNameCode(country);
                profileGender.setText(gender);

                // default image for new user
                if (!image.equals("default_image")) {
                    view.post(() -> mGlideRequestManager
                            .load(image)
                            .placeholder(R.drawable.default_profile_image)
                            .error(R.drawable.default_profile_image)
                            .into(profileImage));
                }

                if (verified.contains("true")) {
                    verified_icon.setVisibility(View.VISIBLE);
                }

                // for fixing dynamic cancel / friend / unfriend / accept button
                friendRequestReference.child(senderID)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                // if in database has these data then, execute conditions below
                                if (dataSnapshot.hasChild(receiver_userID)) {
                                    String requestType = dataSnapshot.child(receiver_userID)
                                            .child("request_type").getValue().toString();

                                    if (requestType.equals("sent")) {
                                        CURRENT_STATE = "request_sent";
                                        sendFriendRequest_Button.setText(getString(R.string.cancel_friend_request));

                                        declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                        declineFriendRequest_Button.setEnabled(false);

                                    } else if (requestType.equals("received")) {
                                        CURRENT_STATE = "request_received";
                                        sendFriendRequest_Button.setText(getString(R.string.accept_friend_request));

                                        declineFriendRequest_Button.setVisibility(View.VISIBLE);
                                        declineFriendRequest_Button.setEnabled(true);


                                        declineFriendRequest_Button.setOnClickListener(v ->
                                                declineFriendRequest()
                                        );
                                    }
                                } else {
                                    friendsDatabaseReference.child(senderID)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(DataSnapshot dataSnapshot) {
                                                    if (dataSnapshot.exists()) {
                                                        if (dataSnapshot.hasChild(receiver_userID)) {
                                                            CURRENT_STATE = "friends";
                                                            sendFriendRequest_Button.setText(getString(R.string.unfriend_this_person));

                                                            declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                                            declineFriendRequest_Button.setEnabled(false);
                                                        }
                                                    }
                                                }
                                                @Override
                                                public void onCancelled(DatabaseError databaseError) {
                                                }
                                            });
                                }
                            }
                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        declineFriendRequest_Button.setVisibility(View.GONE);
        declineFriendRequest_Button.setEnabled(false);

        /** Send / Cancel / Accept / Unfriend >> request mechanism */
        if (!senderID.equals(receiver_userID)) { // condition for current owner / sender id
            sendFriendRequest_Button.setOnClickListener(v -> {
                sendFriendRequest_Button.setEnabled(false);

                if (CURRENT_STATE.equals("not_friends")) {
                    sendFriendRequest();

                } else if (CURRENT_STATE.equals("request_sent")) {
                    cancelFriendRequest();

                } else if (CURRENT_STATE.equals("request_received")) {
                    acceptFriendRequest();

                } else if (CURRENT_STATE.equals("friends")) {
                    unfriendPerson();

                }

            });
        } else {
            sendFriendRequest_Button.setVisibility(View.INVISIBLE);
            declineFriendRequest_Button.setVisibility(View.INVISIBLE);
            go_my_profile.setVisibility(View.VISIBLE);
            go_my_profile.setOnClickListener(v -> {
                Intent intent = new Intent(ChatProfileActivity.this, ProfileActivity.class);
                startActivity(intent);
                finish();
            });
        }


    } // ending OnCreate

    // decline friend request
    private void declineFriendRequest() {
        //for declination, delete data from friends_request nodes
        // delete from, sender >> receiver > values
        friendRequestReference.child(senderID).child(receiver_userID).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // delete from, receiver >> sender > values
                        friendRequestReference.child(receiver_userID).child(senderID).removeValue()
                                .addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        // after deleting data, just set button attributes
                                        sendFriendRequest_Button.setEnabled(true);
                                        CURRENT_STATE = "not_friends";
                                        sendFriendRequest_Button.setText(getString(R.string.send_friend_request));

                                        declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                        declineFriendRequest_Button.setEnabled(false);
                                    }
                                });
                    }
                });
    }

    // Unfriends
    private void unfriendPerson() {
        //for unfriend, delete data from friends nodes
        // delete from, sender >> receiver > values
        friendsDatabaseReference.child(senderID).child(receiver_userID).removeValue()
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {
                        friendsDatabaseReference.child(receiver_userID).child(senderID).removeValue()
                                .addOnCompleteListener(task1 -> {

                                    sendFriendRequest_Button.setEnabled(true);
                                    CURRENT_STATE = "not_friends";
                                    sendFriendRequest_Button.setText(getString(R.string.send_friend_request));

                                    declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                    declineFriendRequest_Button.setEnabled(false);

                                });
                    }
                });
    }
    // accept friends
    private void acceptFriendRequest() {
        Calendar myCalendar = Calendar.getInstance();
        SimpleDateFormat currentDate = new SimpleDateFormat("EEEE, dd MMM, yyyy");
        final String friendshipDate = currentDate.format(myCalendar.getTime());

        friendsDatabaseReference.child(senderID).child(receiver_userID).child("date").setValue(friendshipDate)
                .addOnCompleteListener(task ->
                        friendsDatabaseReference.child(receiver_userID).child(senderID).child("date").setValue(friendshipDate)
                        .addOnCompleteListener(task1 -> {
                            /**
                             *  because of accepting friend request,
                             *  there have no more request them. So, for delete these node
                             */
                            friendRequestReference.child(senderID).child(receiver_userID).removeValue()
                                    .addOnCompleteListener(task2 -> {
                                        if (task2.isSuccessful()) {
                                            // delete from users friend_requests node, receiver >> sender > values
                                            friendRequestReference.child(receiver_userID).child(senderID).removeValue()
                                                    .addOnCompleteListener(task3 -> {
                                                        if (task3.isSuccessful()) {
                                                            // after deleting data, just set button attributes
                                                            sendFriendRequest_Button.setEnabled(true);
                                                            CURRENT_STATE = "friends";
                                                            sendFriendRequest_Button.setText(getString(R.string.unfriend_this_person));

                                                            declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                                            declineFriendRequest_Button.setEnabled(false);
                                                        }
                                                    });
                                        }
                                    });
                        }));
    }

    // cancel friend request
    private void cancelFriendRequest() {
        friendRequestReference.child(senderID).child(receiver_userID).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // delete from, receiver >> sender > values
                        friendRequestReference.child(receiver_userID).child(senderID).removeValue()
                                .addOnCompleteListener(task1 -> {
                                    if (task1.isSuccessful()) {
                                        // after deleting data, just set button attributes
                                        sendFriendRequest_Button.setEnabled(true);
                                        CURRENT_STATE = "not_friends";
                                        sendFriendRequest_Button.setText(getString(R.string.send_friend_request));

                                        declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                        declineFriendRequest_Button.setEnabled(false);
                                    }
                                });
                    }
                });
    }

    // send friend request
    private void sendFriendRequest() {
        // insert or, put data to >> sender >> receiver >> request_type >> sent
        friendRequestReference.child(senderID).child(receiver_userID)
                .child("request_type").setValue("sent")
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        // change or, put data to >> receiver >> sender>> request_type >> received
                        friendRequestReference.child(receiver_userID).child(senderID)
                                .child("request_type").setValue("received")
                                .addOnCompleteListener(task1 -> {

                                    if (task1.isSuccessful()) {

                                        //Request notification mechanism
                                        HashMap<String, String> notificationData = new HashMap<>();
                                        notificationData.put("from", senderID);
                                        notificationData.put("type", "request");

                                        notificationDatabaseReference.child(receiver_userID).push().setValue(notificationData)
                                                .addOnCompleteListener(task2 -> {

                                                    if (task2.isSuccessful()) {
                                                        // Request currency_main mechanism
                                                        sendFriendRequest_Button.setEnabled(true);
                                                        CURRENT_STATE = "request_sent";
                                                        sendFriendRequest_Button.setText(getString(R.string.cancel_friend_request));

                                                        declineFriendRequest_Button.setVisibility(View.INVISIBLE);
                                                        declineFriendRequest_Button.setEnabled(false);
                                                    }
                                                });
                                    }
                                });
                    }
                });
    }
}
package com.videocalllive.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class CreatorApplicationActivity extends Activity {
    private static final String OWNER_ADMIN_UID = "cmnDofW7OcYP2FvTV2KA1uTARM22";
    private static final int REQUEST_SELFIE = 731;
    private LinearLayout root;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private String uid;
    private boolean quickLogin;
    private EditText nameInput, ageInput, countryInput, bioInput;
    private ImageView selfiePreview;
    private boolean selfieCaptured = false;
    private DocumentSnapshot account;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vcl", 0);
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        quickLogin = prefs.getBoolean("quick", false);
        root = Ui.root(this);
        setContentView(root);
        if (uid == null) { renderMessage("Please log in before opening Creator Application."); return; }
        if (isOwner()) { renderOwnerPage(); return; }
        renderLoading();
        db.collection("users").document(uid).get()
            .addOnSuccessListener(this, doc -> { account = doc; renderFromAccount(doc); })
            .addOnFailureListener(this, e -> renderMessage("Could not check your application status. Check your internet connection and try again. " + e.getMessage()));
    }

    private boolean isOwner() { return OWNER_ADMIN_UID.equals(uid); }

    private void renderLoading() {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        root.addView(Ui.body(this, "Checking your application status…"));
    }

    private void renderOwnerPage() {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        root.addView(Ui.body(this, "This is the owner admin account. Creator applications cannot be submitted from the admin account."));
        Button admin = Ui.button(this, "Admin — Review Applications");
        root.addView(admin);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminReviewActivity.class)));
        Button back = Ui.secondary(this, "Back to My Profile");
        root.addView(back); back.setOnClickListener(v -> finish());
    }

    private void renderMessage(String message) {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        root.addView(Ui.body(this, message));
        Button back = Ui.secondary(this, "Back to My Profile");
        root.addView(back); back.setOnClickListener(v -> finish());
    }

    private void renderFromAccount(DocumentSnapshot doc) {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        String status = doc.getString("creatorApplicationStatus");
        if (status == null || status.trim().isEmpty()) status = doc.getString("creatorStatus");
        if (status == null || status.trim().isEmpty()) status = "not_applied";
        if ("pending".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "pending").apply();
            root.addView(Ui.body(this, "Application status: Pending\n\nYour application is waiting for admin review. Creator access and earnings remain locked until approval."));
        } else if ("approved".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "approved").apply();
            root.addView(Ui.body(this, "Application status: Approved\n\nYour creator application has been approved."));
        } else if ("rejected".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "rejected").apply();
            root.addView(Ui.body(this, "Application status: Rejected\n\nYou may submit a new application after reviewing your details."));
            addApplicationForm(doc);
        } else {
            prefs.edit().remove("creator_status").apply();
            if (quickLogin) {
                root.addView(Ui.body(this, "Your current account is Quick Login. Verify with a phone number before applying."));
                Button verify = Ui.button(this, "Verify with Phone Number");
                root.addView(verify); verify.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            } else {
                root.addView(Ui.body(this, "Complete the form and take a selfie to continue. Admin approval is required."));
                addApplicationForm(doc);
            }
        }
        Button back = Ui.secondary(this, "Back to My Profile");
        root.addView(back); back.setOnClickListener(v -> finish());
    }

    private void addApplicationForm(DocumentSnapshot doc) {
        root.addView(Ui.title(this, "Creator Application Form"));
        nameInput = Ui.input(this, "Full name");
        ageInput = Ui.input(this, "Age"); ageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countryInput = Ui.input(this, "Country");
        bioInput = Ui.input(this, "Short introduction");
        bioInput.setSingleLine(false); bioInput.setMinLines(2);
        root.addView(nameInput); root.addView(ageInput); root.addView(countryInput); root.addView(bioInput);
        String oldName = doc.getString("creatorName"); if (oldName == null) oldName = doc.getString("displayName");
        if (oldName != null) nameInput.setText(oldName);
        String oldCountry = doc.getString("country"); if (oldCountry != null && !"null".equals(oldCountry)) countryInput.setText(oldCountry);
        selfiePreview = new ImageView(this);
        selfiePreview.setAdjustViewBounds(true);
        root.addView(selfiePreview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 180)));
        Button selfie = Ui.button(this, "Take Selfie");
        root.addView(selfie);
        selfie.setOnClickListener(v -> {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) startActivityForResult(intent, REQUEST_SELFIE);
            else Ui.toast(this, "No camera app is available");
        });
        root.addView(Ui.body(this, "Note: this captures a selfie on this device; it is not automated face/identity verification. A secure verification service and photo upload/review still need to be integrated."));
        Button submit = Ui.button(this, "Submit Creator Application");
        root.addView(submit);
        submit.setOnClickListener(v -> submitApplication(submit));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELFIE && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Object photo = data.getExtras().get("data");
            if (photo instanceof Bitmap) {
                selfiePreview.setImageBitmap((Bitmap) photo);
                selfieCaptured = true;
                Ui.toast(this, "Selfie captured");
            }
        }
    }

    private void submitApplication(Button submit) {
        if (isOwner()) { Ui.toast(this, "Admin account cannot apply as creator"); return; }
        String name = nameInput.getText().toString().trim();
        String ageText = ageInput.getText().toString().trim();
        String country = countryInput.getText().toString().trim();
        String bio = bioInput.getText().toString().trim();
        if (name.isEmpty() || ageText.isEmpty() || country.isEmpty() || bio.isEmpty()) { Ui.toast(this, "Please fill in all fields"); return; }
        int age;
        try { age = Integer.parseInt(ageText); } catch (NumberFormatException ex) { Ui.toast(this, "Enter a valid age"); return; }
        if (age < 18 || age > 100) { Ui.toast(this, "Creator applications are available only to adults (18+)"); return; }
        if (!selfieCaptured) { Ui.toast(this, "Please take a selfie first"); return; }
        submit.setEnabled(false); submit.setText("Submitting…");
        Map<String,Object> data = new HashMap<>();
        data.put("creatorName", name);
        data.put("creatorAge", age);
        data.put("country", country);
        data.put("creatorBio", bio);
        data.put("creatorSelfieCaptured", true);
        data.put("creatorApplicationStatus", "pending");
        data.put("creatorApplicationSubmittedAt", System.currentTimeMillis());
        data.put("creatorStatus", "pending");
        db.collection("users").document(uid).set(data, SetOptions.merge())
          .addOnSuccessListener(this, unused -> {
            prefs.edit().putString("creator_status", "pending").apply();
            Ui.toast(this, "Creator application submitted");
            recreate();
          })
          .addOnFailureListener(this, e -> {
            submit.setEnabled(true); submit.setText("Submit Creator Application");
            Ui.toast(this, "Could not submit application: " + e.getMessage());
          });
    }
}
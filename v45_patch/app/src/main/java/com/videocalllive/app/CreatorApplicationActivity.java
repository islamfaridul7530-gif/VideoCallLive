package com.videocalllive.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class CreatorApplicationActivity extends Activity {
    private static final String OWNER_ADMIN_UID = "cmnDofW7OcYP2FvTV2KA1uTARM22";
    private LinearLayout root;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private String uid;
    private boolean quickLogin;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vcl", 0);
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        quickLogin = prefs.getBoolean("quick", false);
        root = Ui.root(this);
        root.addView(Ui.backTitle(this, "Become a Creator"));
        setContentView(root);

        if (uid == null) {
            renderMessage("Please log in before opening Creator Application.");
            return;
        }
        renderLoading();
        // Firestore is the source of truth. Do not show Pending from an old local preference.
        db.collection("users").document(uid).get()
            .addOnSuccessListener(this, this::renderFromAccount)
            .addOnFailureListener(this, e -> {
                root.removeAllViews();
                root.addView(Ui.backTitle(this, "Become a Creator"));
                addAdminButtonIfOwner();
                root.addView(Ui.body(this,
                    "Could not check your application status from the server. Please check your internet connection and try again.\n\n" +
                    "Details: " + e.getMessage()));
                Button retry = Ui.button(this, "Retry");
                root.addView(retry);
                retry.setOnClickListener(v -> recreate());
            });
    }

    private boolean isOwner() {
        return OWNER_ADMIN_UID.equals(uid);
    }

    private void renderLoading() {
        root.addView(Ui.body(this, "Checking your application status…"));
    }

    private void renderMessage(String message) {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        addAdminButtonIfOwner();
        root.addView(Ui.body(this, message));
        Button back = Ui.secondary(this, "Back to My Profile");
        root.addView(back);
        back.setOnClickListener(v -> finish());
    }

    private void addAdminButtonIfOwner() {
        if (!isOwner()) return;
        Button admin = Ui.button(this, "Admin — Review Applications");
        root.addView(admin);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminReviewActivity.class)));
    }

    private void renderFromAccount(DocumentSnapshot doc) {
        root.removeAllViews();
        root.addView(Ui.backTitle(this, "Become a Creator"));
        addAdminButtonIfOwner();

        String status = doc.getString("creatorApplicationStatus");
        if (status == null || status.trim().isEmpty()) {
            status = doc.getString("creatorStatus");
        }
        if (status == null || status.trim().isEmpty()) status = "not_applied";

        if ("pending".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "pending").apply();
            root.addView(Ui.body(this,
                "Application status: Pending\n\nYour application was submitted and is waiting for admin review. " +
                "Creator access and earnings remain locked until approval."));
        } else if ("approved".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "approved").apply();
            root.addView(Ui.body(this,
                "Application status: Approved\n\nYour creator application has been approved."));
        } else if ("rejected".equalsIgnoreCase(status)) {
            prefs.edit().putString("creator_status", "rejected").apply();
            root.addView(Ui.body(this,
                "Application status: Rejected\n\nYour application was not approved. Please contact support if you need more information."));
            addApplyButton();
        } else {
            prefs.edit().remove("creator_status").apply();
            root.addView(Ui.body(this,
                "You have not submitted a creator application yet. Creator access is separate from selecting Female. " +
                "Applications require a verified account and admin approval."));
            if (quickLogin) {
                root.addView(Ui.body(this,
                    "Your current account is Quick Login. Please verify with a phone number before applying."));
                Button verify = Ui.button(this, "Verify with Phone Number");
                root.addView(verify);
                verify.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            } else {
                addApplyButton();
            }
        }

        Button back = Ui.secondary(this, "Back to My Profile");
        root.addView(back);
        back.setOnClickListener(v -> finish());
    }

    private void addApplyButton() {
        Button apply = Ui.button(this, "Submit Creator Application");
        root.addView(apply);
        apply.setOnClickListener(v -> submitApplication(apply));
    }

    private void submitApplication(Button apply) {
        if (uid == null) {
            Ui.toast(this, "Please login first");
            return;
        }
        apply.setEnabled(false);
        apply.setText("Submitting…");
        Map<String, Object> data = new HashMap<>();
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
                apply.setEnabled(true);
                apply.setText("Submit Creator Application");
                Ui.toast(this, "Could not submit application: " + e.getMessage());
            });
    }
}

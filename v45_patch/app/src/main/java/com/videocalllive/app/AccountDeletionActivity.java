package com.videocalllive.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import java.util.Collections;

public class AccountDeletionActivity extends Activity {
    private static final String PRIVACY_URL =
        "https://github.com/islamfaridul7530-gif/VideoCallLive/blob/main/docs/PRIVACY-POLICY.md";
    private Button deleteButton;
    private CheckBox confirmation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.root(this);
        root.addView(Ui.backTitle(this, "Delete Account"));
        root.addView(Ui.body(this,
            "This permanently deletes your VideoCallLive account. Your profile, creator application and selfie files, app-side payment-order records, support tickets, chats and call records stored by VideoCallLive will be removed. Shared chat/call records will also disappear for the other participant. This cannot be undone."));
        root.addView(Ui.body(this,
            "Payment providers may retain their own transaction records under their policies or legal obligations. Content another user saved outside the app cannot be removed by us."));
        confirmation = new CheckBox(this);
        confirmation.setTextColor(Color.WHITE);
        confirmation.setText("I understand that my account and related app data will be permanently deleted.");
        root.addView(confirmation);

        deleteButton = Ui.button(this, "Delete Account Permanently");
        deleteButton.setEnabled(false);
        root.addView(deleteButton);
        confirmation.setOnCheckedChangeListener((button, checked) -> deleteButton.setEnabled(checked));
        deleteButton.setOnClickListener(v -> confirmDeletion());

        Button privacy = Ui.secondary(this, "Read Privacy Policy");
        root.addView(privacy);
        privacy.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))); }
            catch (Exception e) { Ui.toast(this, "Could not open the Privacy Policy."); }
        });
        setContentView(root);

        if (FirebaseAuth.getInstance().getUid() == null) {
            deleteButton.setEnabled(false);
            confirmation.setEnabled(false);
            root.addView(Ui.body(this, "No signed-in account was found. Sign in to the account you want to delete first."));
        }
    }

    private void confirmDeletion() {
        new AlertDialog.Builder(this)
            .setTitle("Delete account permanently?")
            .setMessage("Your account and associated app data will be deleted. This action cannot be reversed.")
            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
            .setPositiveButton("Delete permanently", (d, w) -> deleteAccount())
            .show();
    }

    private void deleteAccount() {
        if (FirebaseAuth.getInstance().getUid() == null) {
            Ui.toast(this, "Please sign in to the account you want to delete.");
            return;
        }
        deleteButton.setEnabled(false);
        deleteButton.setText("Deleting account…");
        FirebaseFunctions.getInstance("asia-south1")
            .getHttpsCallable("deleteMyAccount")
            .call(Collections.singletonMap("confirmation", "DELETE"))
            .addOnSuccessListener(result -> {
                FirebaseAuth.getInstance().signOut();
                getSharedPreferences("vcl", MODE_PRIVATE).edit().clear().apply();
                getSharedPreferences("vcl_crash", MODE_PRIVATE).edit().clear().apply();
                Intent home = new Intent(this, MainActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(home);
                finishAffinity();
            })
            .addOnFailureListener(error -> {
                deleteButton.setEnabled(confirmation.isChecked());
                deleteButton.setText("Delete Account Permanently");
                Ui.toast(this, "Deletion could not be completed. Please retry or contact support.");
            });
    }
}

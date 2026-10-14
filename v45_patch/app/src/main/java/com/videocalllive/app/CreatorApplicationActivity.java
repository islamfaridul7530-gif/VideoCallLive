package com.videocalllive.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class CreatorApplicationActivity extends Activity {
    private static final String OWNER_ADMIN_UID = "cmnDofW7OcYP2FvTV2KA1uTARM22";
    private static final int REQUEST_SELFIE = 731;
    private LinearLayout root;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String uid;
    private boolean quickLogin;
    private EditText nameInput, ageInput, countryInput, bioInput;
    private ImageView selfiePreview;
    private Bitmap selfieBitmap;
    private DocumentSnapshot account;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("vcl", 0);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        uid = FirebaseAuth.getInstance().getUid();
        quickLogin = prefs.getBoolean("quick", false);
        root = Ui.root(this); setContentView(root);
        if (uid == null) { renderMessage("Please log in before opening Creator Application."); return; }
        if (isOwner()) { renderOwnerPage(); return; }
        renderLoading();
        db.collection("users").document(uid).get()
          .addOnSuccessListener(this, doc -> { account = doc; renderFromAccount(doc); })
          .addOnFailureListener(this, e -> renderMessage("Could not check application status. Check your connection and try again."));
    }
    private boolean isOwner() { return OWNER_ADMIN_UID.equals(uid); }
    private void renderLoading() { root.removeAllViews(); root.addView(Ui.backTitle(this,"Become a Creator")); root.addView(Ui.body(this,"Checking your application status…")); }
    private void renderOwnerPage() {
        root.removeAllViews(); root.addView(Ui.backTitle(this,"Become a Creator"));
        root.addView(Ui.body(this,"Owner admin accounts cannot submit creator applications."));
        Button admin=Ui.button(this,"Admin — Review Applications"); root.addView(admin);
        admin.setOnClickListener(v->startActivity(new Intent(this,AdminReviewActivity.class)));
        Button back=Ui.secondary(this,"Back to My Profile");root.addView(back);back.setOnClickListener(v->finish());
    }
    private void renderMessage(String s) { root.removeAllViews();root.addView(Ui.backTitle(this,"Become a Creator"));root.addView(Ui.body(this,s));Button b=Ui.secondary(this,"Back to My Profile");root.addView(b);b.setOnClickListener(v->finish());}
    private void renderFromAccount(DocumentSnapshot doc) {
        root.removeAllViews();root.addView(Ui.backTitle(this,"Become a Creator"));
        String status=doc.getString("creatorApplicationStatus");if(status==null||status.trim().isEmpty())status=doc.getString("creatorStatus");if(status==null||status.trim().isEmpty())status="not_applied";
        if("pending".equalsIgnoreCase(status)) { prefs.edit().putString("creator_status","pending").apply();root.addView(Ui.body(this,"Application status: Pending\n\nYour application is waiting for admin review. Creator access remains locked until approval.")); }
        else if("approved".equalsIgnoreCase(status)) { prefs.edit().putString("creator_status","approved").apply();root.addView(Ui.body(this,"Application status: Approved")); }
        else {
            prefs.edit().remove("creator_status").apply();
            if(quickLogin) { root.addView(Ui.body(this,"Verify your account with a phone number before applying."));Button b=Ui.button(this,"Verify with Phone Number");root.addView(b);b.setOnClickListener(v->startActivity(new Intent(this,LoginActivity.class))); }
            else { if("rejected".equalsIgnoreCase(status))root.addView(Ui.body(this,"Application status: Rejected\n\nYou may submit a new application after reviewing your details."));else root.addView(Ui.body(this,"Complete the form and capture a selfie. A face-presence check runs before upload; this is not identity or liveness verification."));
                addApplicationForm(doc);
            }
        }
        Button back=Ui.secondary(this,"Back to My Profile");root.addView(back);back.setOnClickListener(v->finish());
    }
    private void addApplicationForm(DocumentSnapshot doc) {
        root.addView(Ui.title(this,"Creator Application Form"));
        nameInput=Ui.input(this,"Full name");ageInput=Ui.input(this,"Age");ageInput.setInputType(InputType.TYPE_CLASS_NUMBER);countryInput=Ui.input(this,"Country");bioInput=Ui.input(this,"Short introduction");bioInput.setSingleLine(false);bioInput.setMinLines(2);
        root.addView(nameInput);root.addView(ageInput);root.addView(countryInput);root.addView(bioInput);
        String oldName=doc.getString("creatorName");if(oldName==null)oldName=doc.getString("displayName");if(oldName!=null)nameInput.setText(oldName);
        String oldCountry=doc.getString("country");if(oldCountry!=null&&!"null".equals(oldCountry))countryInput.setText(oldCountry);
        selfiePreview=new ImageView(this);selfiePreview.setAdjustViewBounds(true);root.addView(selfiePreview,new LinearLayout.LayoutParams(-1,Ui.dp(this,180)));
        Button take=Ui.button(this,"Take Selfie");root.addView(take);take.setOnClickListener(v->{Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);if(i.resolveActivity(getPackageManager())!=null)startActivityForResult(i,REQUEST_SELFIE);else Ui.toast(this,"No camera app is available");});
        root.addView(Ui.body(this,"A face-presence check is performed on-device. It does not prove identity, age, or liveness. Your selfie will be uploaded for admin review."));
        Button submit=Ui.button(this,"Verify Face & Submit Application");root.addView(submit);submit.setOnClickListener(v->submitApplication(submit));
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_SELFIE&&resultCode==RESULT_OK&&data!=null&&data.getExtras()!=null) {
            Object photo=data.getExtras().get("data");
            if(photo instanceof Bitmap) { selfieBitmap=(Bitmap)photo;selfiePreview.setImageBitmap(selfieBitmap);Ui.toast(this,"Selfie captured. Face check will run on submit."); }
        }
    }
    private void submitApplication(Button submit) {
        if(isOwner()){Ui.toast(this,"Admin account cannot apply as creator");return;}
        String name=nameInput.getText().toString().trim(),ageText=ageInput.getText().toString().trim(),country=countryInput.getText().toString().trim(),bio=bioInput.getText().toString().trim();
        if(name.isEmpty()||ageText.isEmpty()||country.isEmpty()||bio.isEmpty()){Ui.toast(this,"Please fill in all fields");return;}
        int age;try{age=Integer.parseInt(ageText);}catch(Exception e){Ui.toast(this,"Enter a valid age");return;}
        if(age<18||age>100){Ui.toast(this,"Creator applications are available only to adults (18+)");return;}
        if(selfieBitmap==null){Ui.toast(this,"Please take a selfie first");return;}
        submit.setEnabled(false);submit.setText("Checking face…");
        FaceDetectorOptions options=new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE).setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).build();
        FaceDetector detector=FaceDetection.getClient(options);
        detector.process(InputImage.fromBitmap(selfieBitmap,0))
          .addOnSuccessListener(faces->{detector.close();if(faces==null||faces.size()!=1){submit.setEnabled(true);submit.setText("Verify Face & Submit Application");Ui.toast(this,"Please capture a clear selfie with exactly one visible face");return;}uploadSelfie(submit,name,age,country,bio);})
          .addOnFailureListener(e->{detector.close();submit.setEnabled(true);submit.setText("Verify Face & Submit Application");Ui.toast(this,"Face detection failed. Please try again.");});
    }
    private void uploadSelfie(Button submit,String name,int age,String country,String bio) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();selfieBitmap.compress(Bitmap.CompressFormat.JPEG,85,out);byte[] bytes=out.toByteArray();
        String path="creatorApplications/"+uid+"/selfie.jpg";StorageReference ref=storage.getReference().child(path);
        submit.setText("Uploading selfie…");
        ref.putBytes(bytes).continueWithTask(task->{if(!task.isSuccessful())throw task.getException();return ref.getDownloadUrl();})
          .addOnSuccessListener(uri->{
            Map<String,Object> data=new HashMap<>();data.put("creatorName",name);data.put("creatorAge",age);data.put("country",country);data.put("creatorBio",bio);data.put("creatorSelfiePath",path);data.put("creatorFaceDetected",true);data.put("creatorApplicationStatus","pending");data.put("creatorApplicationSubmittedAt",System.currentTimeMillis());data.put("creatorStatus","pending");
            db.collection("users").document(uid).set(data,SetOptions.merge()).addOnSuccessListener(v->{prefs.edit().putString("creator_status","pending").apply();Ui.toast(this,"Selfie uploaded and application submitted");recreate();}).addOnFailureListener(e->{submit.setEnabled(true);submit.setText("Verify Face & Submit Application");Ui.toast(this,"Application save failed. Please retry.");});
          }).addOnFailureListener(e->{submit.setEnabled(true);submit.setText("Verify Face & Submit Application");Ui.toast(this,"Selfie upload failed. Check Firebase Storage setup and rules.");});
    }
}
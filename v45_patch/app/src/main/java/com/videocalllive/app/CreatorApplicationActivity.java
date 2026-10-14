package com.videocalllive.app;
import android.app.*;import android.os.*;import android.content.*;import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
public class CreatorApplicationActivity extends Activity{
 private static final String OWNER_ADMIN_UID = "cmnDofW7OcYP2FvTV2KA1uTARM22";
 public void onCreate(Bundle b){
  super.onCreate(b);
  android.content.SharedPreferences p=getSharedPreferences("vcl",0);
  LinearLayout r=Ui.root(this);r.addView(Ui.backTitle(this,"Become a Creator"));
  boolean quick=p.getBoolean("quick",false);
  String status=p.getString("creator_status","not_applied");
  boolean isOwner=OWNER_ADMIN_UID.equals(FirebaseAuth.getInstance().getUid());
  if("pending".equals(status)){
   r.addView(Ui.body(this,"Application status: Pending\n\nCreator access is not active yet. Paid calls and earnings stay locked until approval."));
   if(isOwner){
    Button admin=Ui.button(this,"Admin — Review Applications");r.addView(admin);
    admin.setOnClickListener(v->startActivity(new Intent(this,AdminReviewActivity.class)));
   }
   Button back=Ui.secondary(this,"Back to My Profile");r.addView(back);back.setOnClickListener(v->finish());setContentView(r);return;
  }
  if(isOwner){
   Button admin=Ui.button(this,"Admin — Review Applications");r.addView(admin);
   admin.setOnClickListener(v->startActivity(new Intent(this,AdminReviewActivity.class)));
  }
  r.addView(Ui.body(this,"Creator access is separate from choosing Female. Eligibility: 18+, completed profile, verified account and approval. Call rate will be controlled by the app/admin."));
  if(quick){r.addView(Ui.body(this,"Your current account is Quick Login. Creator application requires a verified phone account."));Button verify=Ui.button(this,"Verify with Phone Number");r.addView(verify);verify.setOnClickListener(v->startActivity(new Intent(this,LoginActivity.class)));}
  else{Button apply=Ui.button(this,"Submit Creator Application");r.addView(apply);apply.setOnClickListener(v->{
   String uid=FirebaseAuth.getInstance().getUid(); if(uid==null){Ui.toast(this,"Please login first");return;}
   HashMap<String,Object> data=new HashMap<>();data.put("creatorApplicationStatus","pending");data.put("creatorApplicationSubmittedAt",System.currentTimeMillis());data.put("creatorStatus","pending");
   FirebaseFirestore.getInstance().collection("users").document(uid).set(data,com.google.firebase.firestore.SetOptions.merge())
    .addOnSuccessListener(x->{p.edit().putString("creator_status","pending").apply();Ui.toast(this,"Creator application submitted");recreate();})
    .addOnFailureListener(e->Ui.toast(this,"Could not submit application: "+e.getMessage()));
  });}
  setContentView(r);
 }
}

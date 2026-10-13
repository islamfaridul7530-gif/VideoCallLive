#!/usr/bin/env python3
from pathlib import Path
import sys

project = Path(sys.argv[1])
base = project / "app/src/main/java/com/videocalllive/app"

def replace_once(path, old, new, label="unnamed patch"):
    p = base / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count} in {p}")
    p.write_text(text.replace(old, new, 1))
    print(f"Patched {label}")

# Quick Login users can provide a phone number for Cashfree checkout without
# changing their anonymous Firebase UID or switching login methods.
replace_once(
    "TopUpActivity.java",
    '''        functions = FirebaseFunctions.getInstance("asia-south1");
        try { CFPaymentGatewayService.getInstance().setCheckoutCallback(this); }
        catch (CFException e) { }
        draw();
        loadBalance();''',
    '''        functions = FirebaseFunctions.getInstance("asia-south1");
        try { CFPaymentGatewayService.getInstance().setCheckoutCallback(this); }
        catch (CFException e) { }
        String gender = getSharedPreferences("vcl", 0).getString("gender", "");
        if ("female".equalsIgnoreCase(gender)) {
            LinearLayout blocked = Ui.root(this);
            blocked.addView(Ui.backTitle(this, "Coins unavailable"));
            blocked.addView(Ui.body(this, "Coin purchases are available only for Male accounts. Your Female account and Creator profile are unchanged."));
            Button home = Ui.button(this, "Back to Home");
            blocked.addView(home);
            home.setOnClickListener(v -> {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            });
            setContentView(blocked);
            return;
        }
        draw();
        loadBalance();''',
    "female coin shop UI guard"
)

start_old = '''    private void startPayment(String packId) {
        if(FirebaseAuth.getInstance().getCurrentUser()==null){Ui.toast(this,"Please login first");return;}
        String phone=FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();
        if(phone==null || phone.replaceAll("\\\\D","").length()<10){
            Ui.toast(this,"This test payment needs a phone-number login"); return;
        }
        Map<String,Object> data=new HashMap<>(); data.put("packId",packId); data.put("phone",phone);
        Ui.toast(this,"Creating Cashfree payment…");
        functions.getHttpsCallable("createCashfreeOrder").call(data).addOnSuccessListener(result->{
            try {
                Map<?,?> m=(Map<?,?>)result.getData();
                String orderId=String.valueOf(m.get("orderId"));
                String session=String.valueOf(m.get("paymentSessionId"));
                pendingOrderId=orderId;
                openCheckout(orderId,session);
            } catch(Exception e){ Ui.toast(this,"Payment setup error"); }
        }).addOnFailureListener(e->Ui.toast(this,"Payment setup failed: "+e.getMessage()));
    }
'''
start_new = '''    private void startPayment(String packId) {
        if(FirebaseAuth.getInstance().getCurrentUser()==null){Ui.toast(this,"Please login first");return;}
        String phone=FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();
        if(phone==null || phone.replaceAll("\\\\D","").length()<10){
            EditText phoneInput = new EditText(this);
            phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            phoneInput.setHint("10-digit phone number for checkout");
            new AlertDialog.Builder(this)
                .setTitle("Payment checkout phone")
                .setMessage("Quick Login stays on this same account. Enter a phone number only for Cashfree checkout.")
                .setView(phoneInput)
                .setNegativeButton("Cancel", (dialog, which) -> {})
                .setPositiveButton("Continue", (dialog, which) -> {
                    String entered = phoneInput.getText().toString().replaceAll("\\\\D", "");
                    if (entered.length() < 10) {
                        Ui.toast(this, "Enter a valid 10-digit phone number");
                        return;
                    }
                    createOrder(packId, entered.substring(entered.length() - 10));
                })
                .show();
            return;
        }
        createOrder(packId, phone.replaceAll("\\\\D", "").substring(phone.replaceAll("\\\\D", "").length() - 10));
    }

    private void createOrder(String packId, String phone) {
        Map<String,Object> data=new HashMap<>(); data.put("packId",packId); data.put("phone",phone);
        Ui.toast(this,"Creating Cashfree payment…");
        functions.getHttpsCallable("createCashfreeOrder").call(data).addOnSuccessListener(result->{
            try {
                Map<?,?> m=(Map<?,?>)result.getData();
                String orderId=String.valueOf(m.get("orderId"));
                String session=String.valueOf(m.get("paymentSessionId"));
                pendingOrderId=orderId;
                openCheckout(orderId,session);
            } catch(Exception e){ Ui.toast(this,"Payment setup error"); }
        }).addOnFailureListener(e->Ui.toast(this,"Payment setup failed: "+e.getMessage()));
    }
'''
replace_once("TopUpActivity.java", start_old, start_new, "Quick Login checkout phone prompt")

# New Creator applications must start pending, offline, and with no withdrawable
# earnings. This does not pretend that face verification has happened.
replace_once(
    "FirebaseHelper.java",
    '''m.put("creatorProfileComplete",true);m.put("online",true);m.put("updatedAt",System.currentTimeMillis());''',
    '''m.put("creatorProfileComplete",true);m.put("creatorStatus","pending");m.put("faceVerificationStatus","not_submitted");m.put("earningsCoins",0L);m.put("withdrawableCoins",0L);m.put("online",false);m.put("updatedAt",System.currentTimeMillis());''',
    "Creator application pending defaults"
)

# Replace the misleading local-only Creator Home with a Firestore status gate.
p = base / "CreatorHomeActivity.java"
p.write_text('''package com.videocalllive.app;
import android.app.*;import android.os.*;import android.content.*;import android.widget.*;import com.google.firebase.auth.FirebaseAuth;import com.google.firebase.firestore.*;
public class CreatorHomeActivity extends Activity{
 public void onCreate(Bundle b){super.onCreate(b);draw();}
 void draw(){
  android.content.SharedPreferences p=getSharedPreferences("vcl",0);
  if(!p.getBoolean("creator_profile_complete",false)){startActivity(new Intent(this,CreatorSetupActivity.class));finish();return;}
  LinearLayout r=Ui.root(this);r.addView(Ui.backTitle(this,"Creator Home"));
  String n=p.getString("creator_name","Creator"),c=p.getString("creator_country",""),bio=p.getString("creator_bio","");int rate=p.getInt("creator_rate",80);
  LinearLayout card=Ui.card(this);card.addView(Ui.title(this,n));card.addView(Ui.body(this,"Country: "+c+"\\n"+(bio.isEmpty()?"Creator profile":bio)+"\\n\\nVideo Call Rate: "+rate+" coins/min"));r.addView(card);
  TextView status=Ui.body(this,"Loading application status…");r.addView(status);
  Button edit=Ui.secondary(this,"Edit Creator Profile");Button home=Ui.secondary(this,"Back to Home");r.addView(edit);r.addView(home);
  edit.setOnClickListener(v->{p.edit().putBoolean("creator_profile_complete",false).apply();startActivity(new Intent(this,CreatorSetupActivity.class));finish();});
  home.setOnClickListener(v->{startActivity(new Intent(this,HomeActivity.class));finish();});
  setContentView(r);
  String uid=FirebaseAuth.getInstance().getUid();
  if(uid==null){status.setText("Please log in again to check your Creator status.");return;}
  FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(d->{
   if(isFinishing())return;
   String approval=d.getString("creatorStatus");String face=d.getString("faceVerificationStatus");
   boolean approved="approved".equalsIgnoreCase(approval);
   boolean verified="verified".equalsIgnoreCase(face);
   if(approved&&verified){
    status.setText("Approved and face verified\\nPaid calls and earnings can be enabled.");
   }else{
    String a=approved?"Approval: approved":"Approval: pending";
    String f=verified?"Face verification: verified":"Face verification: required";
    status.setText("Creator application\\n"+a+"\\n"+f+"\\nPaid calls, earnings and withdrawal remain locked until both checks are complete.");
   }
  }).addOnFailureListener(e->status.setText("Could not load Creator status. Check your internet and retry."));
 }
}
''')
print("CreatorHomeActivity now gates paid features on server status and face verification.")


# Keep the Android price label aligned with the authoritative backend package.
replace_once(
    "TopUpActivity.java",
    '''addPack(r,"₹200  •  220 Coins  •  +10% Bonus", "pack_200", false);''',
    '''addPack(r,"₹200  •  200 Coins", "pack_200", false);''',
    "align ₹200 coin pack label with backend"
)


# Redesign the incoming-call sheet while preserving Firestore call transitions and intents.
replace_once(
    "HomeActivity.java",
    '''  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);panel.setPadding(Ui.dp(this,24),Ui.dp(this,65),Ui.dp(this,24),Ui.dp(this,35));panel.setBackgroundColor(Color.rgb(15,13,35));
  TextView top=label("INCOMING VIDEO CALL",15,Color.rgb(188,158,255));top.setGravity(Gravity.CENTER);panel.addView(top);
  ImageView photo=ProfileImages.avatar(this,null,callerName==null?"User":callerName);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(Ui.dp(this,200),Ui.dp(this,230));pp.setMargins(0,Ui.dp(this,32),0,Ui.dp(this,18));panel.addView(photo,pp);
  TextView info=label(callerName==null?"User":callerName,26,Color.WHITE);info.setGravity(Gravity.CENTER);panel.addView(info);
  TextView country=label("Loading caller details…",16,Color.WHITE);country.setGravity(Gravity.CENTER);panel.addView(country);
  panel.addView(label("● Incoming call",15,Color.rgb(70,220,130)));
  LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,Ui.dp(this,75));ap.topMargin=Ui.dp(this,50);panel.addView(actions,ap);
  Button decline=Ui.secondary(this,"DECLINE");Button accept=Ui.button(this,"ACCEPT");decline.setTextColor(Color.WHITE);accept.setTextColor(Color.WHITE);decline.setTextSize(16);accept.setTextSize(16);decline.setAllCaps(false);accept.setAllCaps(false);actions.addView(decline,new LinearLayout.LayoutParams(0,-1,1));actions.addView(accept,new LinearLayout.LayoutParams(0,-1,1));
  dialog.setContentView(panel);if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);''',
    '''  FrameLayout panel=new FrameLayout(this);
  ImageView backgroundPhoto=ProfileImages.avatar(this,null,callerName==null?"User":callerName);backgroundPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);panel.addView(backgroundPhoto,new FrameLayout.LayoutParams(-1,-1));
  View shade=new View(this);shade.setBackgroundColor(0x33000000);panel.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  TextView info=label(callerName==null?"User":callerName,1,Color.TRANSPARENT);TextView country=label("",1,Color.TRANSPARENT);
  FrameLayout avatarFrame=new FrameLayout(this);android.graphics.drawable.GradientDrawable avatarRing=Ui.bg(Color.WHITE,Ui.dp(this,100));avatarFrame.setBackground(avatarRing);avatarFrame.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,4));
  ImageView photo=ProfileImages.avatar(this,null,callerName==null?"User":callerName);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(Ui.bg(Color.BLACK,Ui.dp(this,100)));photo.setClipToOutline(true);avatarFrame.addView(photo,new FrameLayout.LayoutParams(-1,-1));
  FrameLayout.LayoutParams avatarLp=new FrameLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,112),Gravity.CENTER);avatarLp.bottomMargin=Ui.dp(this,55);panel.addView(avatarFrame,avatarLp);
  LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.setOrientation(LinearLayout.HORIZONTAL);FrameLayout.LayoutParams actionsLp=new FrameLayout.LayoutParams(-1,Ui.dp(this,96),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);actionsLp.bottomMargin=Ui.dp(this,38);panel.addView(actions,actionsLp);
  Button decline=Ui.secondary(this,"✕");Button accept=Ui.button(this,"☎");decline.setContentDescription("Decline call");accept.setContentDescription("Accept call");decline.setTextColor(Color.WHITE);accept.setTextColor(Color.WHITE);decline.setTextSize(28);accept.setTextSize(27);decline.setAllCaps(false);accept.setAllCaps(false);decline.setPadding(0,0,0,0);accept.setPadding(0,0,0,0);decline.setBackground(Ui.bg(Color.rgb(220,45,62),Ui.dp(this,50)));accept.setBackground(Ui.bg(Color.rgb(25,190,105),Ui.dp(this,50)));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,72));dl.setMargins(0,0,Ui.dp(this,36),0);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,72));al.setMargins(Ui.dp(this,36),0,0,0);actions.addView(decline,dl);actions.addView(accept,al);
  dialog.setContentView(panel);if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);''',
    "incoming call full-screen photo and icon-only round actions"
)

replace_once(
    "HomeActivity.java",
    '''ProfileImages.load(photo,ph,n);});''',
    '''ProfileImages.load(photo,ph,n);ProfileImages.load(backgroundPhoto,ph,n);});''',
    "sync incoming caller image to full-screen background"
)


# Admin Creator Review screen: every mutation goes through Admin-only callable functions.
admin_activity = r'''package com.videocalllive.app;
import android.app.*;import android.os.*;import android.widget.*;import java.util.*;import com.google.firebase.functions.*;
public class AdminReviewActivity extends Activity {
 LinearLayout list; TextView status; FirebaseFunctions fn;
 public void onCreate(Bundle b){super.onCreate(b);fn=FirebaseFunctions.getInstance("asia-south1");LinearLayout root=Ui.root(this);root.addView(Ui.backTitle(this,"Admin Creator Review"));status=Ui.body(this,"Loading applications…");root.addView(status);ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));Button reload=Ui.button(this,"Refresh Applications");root.addView(reload);reload.setOnClickListener(v->load());setContentView(root);load();}
 void load(){status.setText("Loading applications…");list.removeAllViews();fn.getHttpsCallable("listCreatorApplicationsForAdmin").call().addOnSuccessListener(r->{Object raw=((Map<?,?>)r.getData()).get("applications");if(!(raw instanceof List)||((List<?>)raw).isEmpty()){status.setText("No creator applications found.");return;}status.setText("Applications: "+((List<?>)raw).size());for(Object o:(List<?>)raw){if(!(o instanceof Map))continue;Map<?,?> a=(Map<?,?>)o;String uid=String.valueOf(a.get("uid"));String name=String.valueOf(a.containsKey("creatorName")?a.get("creatorName"):"Creator");String state=String.valueOf(a.containsKey("creatorStatus")?a.get("creatorStatus"):"pending");String face=String.valueOf(a.containsKey("faceVerificationStatus")?a.get("faceVerificationStatus"):"not_submitted");LinearLayout card=Ui.card(this);card.addView(Ui.title(this,name));card.addView(Ui.body(this,"UID: "+uid+"\nApplication: "+state+"\nFace verification: "+face+"\nCountry: "+String.valueOf(a.get("country"))));Button approve=Ui.button(this,"Approve");Button reject=Ui.secondary(this,"Reject");Button more=Ui.secondary(this,"Request More Info");Button verified=Ui.button(this,"Mark Face Verified");Button faceReject=Ui.secondary(this,"Reject Face Verification");card.addView(approve);card.addView(reject);card.addView(more);card.addView(verified);card.addView(faceReject);approve.setOnClickListener(v->review(uid,"approved","Approved by admin"));reject.setOnClickListener(v->review(uid,"rejected","Rejected by admin"));more.setOnClickListener(v->{EditText input=new EditText(this);input.setHint("What information is needed?");new AlertDialog.Builder(this).setTitle("Request more information").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Send",(d,w)->review(uid,"pending",input.getText().toString())).show();});verified.setOnClickListener(v->face(uid,"verified"));faceReject.setOnClickListener(v->face(uid,"rejected"));list.addView(card);}}).addOnFailureListener(e->{status.setText("Admin access unavailable: "+e.getMessage()+"\nConfigure this Firebase account as an Admin.");});}
 void review(String uid,String decision,String note){Map<String,Object>d=new HashMap<>();d.put("creatorUid",uid);d.put("decision",decision);d.put("note",note);fn.getHttpsCallable("reviewCreatorApplication").call(d).addOnSuccessListener(r->{Toast.makeText(this,"Application updated",Toast.LENGTH_SHORT).show();load();}).addOnFailureListener(e->Toast.makeText(this,"Review failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}
 void face(String uid,String state){Map<String,Object>d=new HashMap<>();d.put("creatorUid",uid);d.put("status",state);fn.getHttpsCallable("setCreatorFaceVerification").call(d).addOnSuccessListener(r->{Toast.makeText(this,"Face status updated",Toast.LENGTH_SHORT).show();load();}).addOnFailureListener(e->Toast.makeText(this,"Face review failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}
}
'''
admin_file = base / "AdminReviewActivity.java"
admin_file.write_text(admin_activity)
print("Created AdminReviewActivity with live Admin-only review actions.")

creator = base / "CreatorHomeActivity.java"
creator_text = creator.read_text()
anchor = 'Button edit=Ui.secondary(this,"Edit Creator Profile");Button home=Ui.secondary(this,"Back to Home");r.addView(edit);r.addView(home);'
if creator_text.count(anchor) != 1:
    raise SystemExit("Creator Home admin entry insertion anchor mismatch")
creator_text = creator_text.replace(anchor, 'Button edit=Ui.secondary(this,"Edit Creator Profile");Button admin=Ui.secondary(this,"Admin Review");Button home=Ui.secondary(this,"Back to Home");r.addView(edit);r.addView(admin);r.addView(home);')
click_anchor = 'edit.setOnClickListener(v->{p.edit().putBoolean("creator_profile_complete",false).apply();startActivity(new Intent(this,CreatorSetupActivity.class));finish();});'
if creator_text.count(click_anchor) != 1:
    raise SystemExit("Creator Home click insertion anchor mismatch")
creator_text = creator_text.replace(click_anchor, click_anchor + 'admin.setOnClickListener(v->startActivity(new Intent(this,AdminReviewActivity.class)));')
creator.write_text(creator_text)

manifest = project / "app/src/main/AndroidManifest.xml"
xml = manifest.read_text()
if "AdminReviewActivity" not in xml:
    marker = "</application>"
    if xml.count(marker) != 1:
        raise SystemExit("Android manifest application marker mismatch")
    xml = xml.replace(marker, '<activity android:name=".AdminReviewActivity" android:exported="false" />\n    ' + marker)
    manifest.write_text(xml)
print("Registered AdminReviewActivity and added Creator Home entry; server enforces Admin privileges.")

# Make Admin Review reachable from the actual Become a Creator/application screen too.
# The application-status page can be different from CreatorHomeActivity in the source ZIP.
setup_patch = base / "CreatorSetupActivity.java"
if setup_patch.exists():
    setup_text = setup_patch.read_text()
    if "AdminReviewActivity.class" not in setup_text:
        import re
        matches = list(re.finditer(r"setContentView\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\);", setup_text))
        if len(matches) != 1:
            raise SystemExit(f"CreatorSetupActivity Admin Review entry: expected one setContentView(variable), got {len(matches)}")
        root_name = matches[0].group(1)
        inject = (
            'Button adminReviewButton=Ui.secondary(this,"Admin Review (admin only)");'
            + root_name + '.addView(adminReviewButton);'
            + 'adminReviewButton.setOnClickListener(v->startActivity(new Intent(this,AdminReviewActivity.class)));\\n  '
        )
        setup_text = setup_text[:matches[0].start()] + inject + setup_text[matches[0].start():]
        setup_patch.write_text(setup_text)
        print("Added Admin Review entry to Become a Creator/application screen.")
    else:
        print("CreatorSetupActivity already has Admin Review entry.")
else:
    print("CreatorSetupActivity.java not found; Creator Home entry remains available.")


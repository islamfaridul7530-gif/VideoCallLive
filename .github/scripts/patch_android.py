#!/usr/bin/env python3
from pathlib import Path
import sys

project = Path(sys.argv[1])
base = project / "app/src/main/java/com/videocalllive/app"

def replace_once(path, old, new, label):
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

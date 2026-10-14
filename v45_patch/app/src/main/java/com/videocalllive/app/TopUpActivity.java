package com.videocalllive.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.cashfree.pg.api.CFPaymentGatewayService;
import com.cashfree.pg.core.api.CFSession;
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback;
import com.cashfree.pg.core.api.exception.CFException;
import com.cashfree.pg.core.api.utils.CFErrorResponse;
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutPayment;
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutTheme;
import java.util.*;

public class TopUpActivity extends Activity implements CFCheckoutResponseCallback {
    private FirebaseFunctions functions;
    private TextView balance;
    private String pendingOrderId = null;
    private boolean verifying = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if ("female".equals(getSharedPreferences("vcl",0).getString("gender",""))) {
            Ui.toast(this,"Coins purchase is available only for male accounts.");
            finish();
            return;
        }
        functions = FirebaseFunctions.getInstance("asia-south1");
        try { CFPaymentGatewayService.getInstance().setCheckoutCallback(this); }
        catch (CFException e) { }
        draw();
        loadBalance();
    }

    private void draw() {
        LinearLayout r=Ui.root(this);
        GradientDrawable page=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(55,13,75),Color.rgb(20,11,35),Ui.BG});
        r.setBackground(page);
        r.setPadding(Ui.dp(this,16),Ui.dp(this,42),Ui.dp(this,16),Ui.dp(this,10));
        r.addView(Ui.title(this,"Coins"));
        balance=Ui.body(this,"🪙 Loading balance…"); balance.setTextColor(Color.WHITE); r.addView(balance);
        TextView note=Ui.body(this,"Test payment: ₹15 → 300 Coins\nCashfree Sandbox"); note.setTextColor(Color.rgb(255,220,120)); r.addView(note);

        addPack(r,"₹15  •  300 Coins  •  TEST", "test_15", true);
        addPack(r,"₹100  •  100 Coins", "pack_100", false);
        addPack(r,"₹200  •  220 Coins  •  +10% Bonus", "pack_200", false);
        addPack(r,"₹500  •  600 Coins  •  BEST VALUE", "pack_500", false);
        addPack(r,"₹1000 • 1300 Coins • +30% Bonus", "pack_1000", false);

        Space sp=new Space(this); r.addView(sp,new LinearLayout.LayoutParams(1,0,1));
        LinearLayout nav=Ui.nav(this,"Coins");
        for(int i=0;i<nav.getChildCount();i++){final int k=i;nav.getChildAt(i).setOnClickListener(v->{
            if(k==0)startActivity(new Intent(this,HomeActivity.class));
            if(k==1)startActivity(new Intent(this,MessagesActivity.class));
            if(k==2)startActivity(new Intent(this,VideoHubActivity.class));
            if(k==4)startActivity(new Intent(this,MyProfileActivity.class));
        });}
        r.addView(nav); setContentView(r);
    }

    private void addPack(LinearLayout root,String label,String packId,boolean test) {
        Button x=Ui.secondary(this,label);
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(100,30,150),Color.rgb(235,45,135)});
        g.setCornerRadius(Ui.dp(this,18));
        if(test) g.setStroke(Ui.dp(this,2),Color.rgb(255,210,80));
        x.setBackground(g); root.addView(x);
        x.setOnClickListener(v->startPayment(packId));
    }

    private void loadBalance() {
        String uid=FirebaseAuth.getInstance().getUid();
        if(uid==null){balance.setText("🪙 0 Coins");return;}
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener((d,e)->{
                if(d!=null && d.exists()) {
                    Long c=d.getLong("coinBalance");
                    balance.setText("🪙 "+(c==null?0:c)+" Coins");
                } else balance.setText("🪙 0 Coins");
            });
    }

    private void startPayment(String packId) {
        if(FirebaseAuth.getInstance().getCurrentUser()==null){Ui.toast(this,"Please login first");return;}
        String phone=FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();
        if(phone==null || phone.replaceAll("\\D","").length()<10){
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

    private void openCheckout(String orderId,String sessionId) {
        try {
            CFSession cfSession=new CFSession.CFSessionBuilder()
                .setEnvironment(CFSession.Environment.SANDBOX)
                .setPaymentSessionID(sessionId)
                .setOrderId(orderId).build();
            CFWebCheckoutTheme theme=new CFWebCheckoutTheme.CFWebCheckoutThemeBuilder()
                .setNavigationBarBackgroundColor("#6A3FD3")
                .setNavigationBarTextColor("#FFFFFF").build();
            CFWebCheckoutPayment payment=new CFWebCheckoutPayment.CFWebCheckoutPaymentBuilder()
                .setSession(cfSession).setCFWebCheckoutUITheme(theme).build();
            CFPaymentGatewayService.getInstance().doPayment(this,payment);
        } catch(CFException e){Ui.toast(this,"Cashfree checkout error: "+e.getMessage());}
    }

    @Override public void onPaymentVerify(String orderID) {
        verifyPayment(orderID);
    }

    @Override public void onPaymentFailure(CFErrorResponse error,String orderID) {
        Ui.toast(this,"Payment not completed");
    }

    private void verifyPayment(String orderId) {
        if(verifying)return; verifying=true;
        Map<String,Object> data=new HashMap<>(); data.put("orderId",orderId);
        functions.getHttpsCallable("verifyCashfreePayment").call(data).addOnSuccessListener(result->{
            verifying=false;
            try {
                Map<?,?> m=(Map<?,?>)result.getData();
                boolean ok=Boolean.TRUE.equals(m.get("success"));
                String status=String.valueOf(m.get("status"));
                if(ok) Ui.toast(this,"Payment successful — coins added");
                else Ui.toast(this,"Payment status: "+status);
                loadBalance();
            } catch(Exception e){Ui.toast(this,"Payment verification error");}
        }).addOnFailureListener(e->{verifying=false;Ui.toast(this,"Verification failed: "+e.getMessage());});
    }
}

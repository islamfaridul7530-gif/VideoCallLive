package com.videocalllive.app;
import android.app.*;import android.os.*;import android.content.*;import android.view.*;import android.widget.*;
import com.cashfree.pg.api.CFPaymentGatewayService;import com.cashfree.pg.core.api.CFException;import com.google.firebase.functions.FirebaseFunctions;
public class TopUpActivity extends Activity{
 private FirebaseFunctions functions;
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
  LinearLayout r=Ui.root(this);r.addView(Ui.backTitle(this,"Coins"));
  r.addView(Ui.body(this,"Buy coins to make video calls. Male accounts only."));
  String[] packs={"100 Coins • ₹15","500 Coins • ₹70","1000 Coins • ₹130"};
  for(String pack:packs){Button buy=Ui.button(this,pack);r.addView(buy);buy.setOnClickListener(v->buy(pack));}
  Button back=Ui.secondary(this,"Back");r.addView(back);back.setOnClickListener(v->finish());setContentView(r);
 }
 private void buy(String pack){Ui.toast(this,"Payment flow will open for "+pack);}
}

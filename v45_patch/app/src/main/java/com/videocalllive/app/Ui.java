package com.videocalllive.app;
import android.app.*;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
public class Ui{
 public static final int BG=Color.rgb(10,11,18),CARD=Color.rgb(22,23,34),TEXT=Color.WHITE,PINK=Color.rgb(255,64,129);
 public static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
 public static LinearLayout root(Activity a){LinearLayout r=new LinearLayout(a);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(a,14),dp(a,35),dp(a,14),0);r.setBackgroundColor(BG);return r;}
 public static TextView title(Activity a,String s){TextView t=new TextView(a);t.setText(s);t.setTextColor(TEXT);t.setTextSize(22);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,dp(a,8),0,dp(a,10));return t;}
 public static TextView body(Activity a,String s){TextView t=new TextView(a);t.setText(s);t.setTextColor(TEXT);t.setTextSize(15);t.setPadding(0,dp(a,6),0,dp(a,10));return t;}
 public static TextView backTitle(Activity a,String s){TextView t=title(a,"‹  "+s);t.setOnClickListener(v->a.finish());return t;}
 public static Button button(Activity a,String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(15);b.setBackground(bg(PINK,dp(a,14)));b.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));return b;}
 public static Button secondary(Activity a,String s){Button b=button(a,s);b.setBackground(bg(CARD,dp(a,14)));return b;}
 public static EditText input(Activity a,String h){EditText e=new EditText(a);e.setHint(h);e.setHintTextColor(Color.rgb(125,128,143));e.setTextColor(TEXT);e.setSingleLine(true);GradientDrawable g=bg(CARD,dp(a,16));g.setStroke(dp(a,1),Color.rgb(58,60,76));e.setBackground(g);e.setPadding(dp(a,16),0,dp(a,16),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,54));p.setMargins(0,dp(a,6),0,dp(a,8));e.setLayoutParams(p);return e;}
 public static LinearLayout card(Activity a){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,14),dp(a,14),dp(a,14),dp(a,14));c.setBackground(bg(CARD,dp(a,20)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(a,7),0,dp(a,7));c.setLayoutParams(p);return c;}
 public static TextView avatar(Activity a,String name){TextView v=new TextView(a);v.setText(name.substring(0,1));v.setTextColor(TEXT);v.setTextSize(34);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(bg(Color.rgb(115,55,155),dp(a,20)));v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(a,155)));return v;}
 public static LinearLayout nav(Activity a,String active){LinearLayout nav=new LinearLayout(a);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(0,dp(a,4),0,0);String[] labels={"⌂\nHome","☏\nChats","▣\nVideo","◎\nCoins","♙\nProfile"};for(int i=0;i<labels.length;i++){String s=labels[i];Button b=new Button(a);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(s.contains(active)?PINK:Color.WHITE);b.setBackgroundColor(Color.TRANSPARENT);b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(a,64),1));if(i==3 && "female".equals(a.getSharedPreferences("vcl",0).getString("gender",""))) b.setVisibility(View.GONE);nav.addView(b);}return nav;}
 public static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_SHORT).show();}
 private static GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
}

package com.example.horizon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Horizon v0.1 launcher for Stinger HORIZON12 / iX212.
// v0.1 = Mojave Home skin (top bar, now-playing placeholder, Jeep cutout, bottom dock)
// with the dock/tiles launching the real stock apps by intent. Clock is live; the
// now-playing card and vehicle status are placeholder until v0.2 wires CarService/PAC/MediaSession.
public class MainActivity extends Activity {
  static final int BG=0xFF07090D, PANEL=0xFF10151C, PANEL2=0xFF171E27,
      BONE=0xFFF4EFE4, MUTED=0xFF9DA6AE, ORANGE=0xFFFF6A00, TEAL=0xFF31CABC;
  float den; Typeface gag; TextView clock; Handler h=new Handler();

  int dp(float v){ return (int)(v*den+0.5f); }
  Typeface gag(){ return gag!=null?gag:Typeface.DEFAULT_BOLD; }

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    den=getResources().getDisplayMetrics().density;
    try { gag=Typeface.createFromAsset(getAssets(),"Gagalin-Regular.otf"); } catch(Exception e){ gag=Typeface.DEFAULT_BOLD; }
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    int pad=dp(22);
    root.setPadding(pad,dp(14),pad,dp(12));
    root.addView(topBar(), row(-1,dp(40)));
    root.addView(middle(), grow());
    root.addView(dock(), row(-1,dp(74)));
    setContentView(root);
    tick();
  }

  // ---------- top bar ----------
  View topBar(){
    LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.HORIZONTAL);
    t.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand=label("ALMNJOY  /  MOJAVE", ORANGE, 20); brand.setLetterSpacing(0.06f);
    brand.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ adminMenu(); }});
    t.addView(brand, grow1());
    clock=label("", BONE, 18);
    t.addView(clock, wrap());
    TextView cp=label("   CARPLAY", TEAL, 18); t.addView(cp, wrap());
    return t;
  }

  // ---------- middle: now-playing card + jeep panel ----------
  View middle(){
    LinearLayout m=new LinearLayout(this); m.setOrientation(LinearLayout.HORIZONTAL);
    m.setPadding(0,dp(14),0,dp(12));
    m.addView(nowPlaying(), colw(0, 0.42f));
    View gap=new View(this); m.addView(gap, new LinearLayout.LayoutParams(dp(16),1));
    m.addView(jeepPanel(), colw(0, 0.58f));
    return m;
  }

  View nowPlaying(){
    LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(round(PANEL, 18)); int p=dp(22); c.setPadding(p,p,p,p);
    c.addView(label("NOW PLAYING  /  SPOTIFY", ORANGE, 14), wrap());
    TextView title=label("GIVE IT AWAY", BONE, 46); title.setPadding(0,dp(6),0,0); c.addView(title, wrap());
    c.addView(label("RED HOT CHILI PEPPERS", BONE, 18), wrap());
    c.addView(waveform(), row(-1,dp(46)));
    LinearLayout pr=new LinearLayout(this); pr.setGravity(Gravity.CENTER_VERTICAL); pr.setPadding(0,dp(10),0,dp(4));
    TextView play=glyph("‖", BONE, 26); play.setBackground(round(ORANGE, 40));
    int ps=dp(52); LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(ps,ps); play.setGravity(Gravity.CENTER);
    pr.addView(play, plp);
    pr.addView(pad(label("  ◀    ▶", BONE, 22)));
    pr.addView(label("     VOL 32", MUTED, 15), grow1r());
    c.addView(pr, wrap());
    LinearLayout q=new LinearLayout(this); q.setOrientation(LinearLayout.HORIZONTAL); q.setPadding(0,dp(14),0,0);
    q.addView(tile("CARPLAY","CONNECTED", new Runnable(){ public void run(){ open("carplay"); }}), colw(0,1f));
    q.addView(spacer());
    q.addView(tile("CAMERAS","READY", new Runnable(){ public void run(){ open("camera"); }}), colw(0,1f));
    q.addView(spacer());
    q.addView(tile("HDMI","INPUT 1", new Runnable(){ public void run(){ open("hdmi"); }}), colw(0,1f));
    c.addView(q, wrap());
    return c;
  }

  View waveform(){
    LinearLayout w=new LinearLayout(this); w.setOrientation(LinearLayout.HORIZONTAL);
    w.setGravity(Gravity.BOTTOM); w.setPadding(0,dp(14),0,0);
    int[] hs={12,20,32,24,16,28,40,30,18,10,22,34,26,14,24,38,30,20,12,26,36,22,14,28,18,10,24,32,20,14,22,30,18,12,26,16};
    for(int i=0;i<hs.length;i++){
      View bar=new View(this);
      bar.setBackgroundColor(i<hs.length*0.42?ORANGE:0x55FF6A00);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(5),dp(hs[i])); lp.rightMargin=dp(3);
      w.addView(bar,lp);
    }
    return w;
  }

  View jeepPanel(){
    FrameLayout f=new FrameLayout(this);
    GradientDrawable g=new GradientDrawable(); g.setColor(PANEL); g.setCornerRadius(dp(18));
    f.setBackground(g);
    View glow=new View(this);
    GradientDrawable rg=new GradientDrawable(); rg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    rg.setColors(new int[]{0x66FF6A00,0x00000000}); rg.setGradientRadius(dp(300)); rg.setCornerRadius(dp(18));
    glow.setBackground(rg);
    f.addView(glow, new FrameLayout.LayoutParams(-1,-1));
    ImageView jeep=new ImageView(this); jeep.setScaleType(ImageView.ScaleType.FIT_CENTER);
    try { InputStream is=getAssets().open("jeep.webp"); Bitmap bm=BitmapFactory.decodeStream(is); jeep.setImageBitmap(bm); is.close(); } catch(Exception e){}
    FrameLayout.LayoutParams jl=new FrameLayout.LayoutParams(-1,-1); jl.setMargins(dp(24),dp(10),dp(24),dp(46));
    f.addView(jeep, jl);
    LinearLayout st=new LinearLayout(this); st.setOrientation(LinearLayout.VERTICAL);
    st.addView(label("VEHICLE STATUS", MUTED, 14), wrap());
    st.addView(label("PARKED / READY", ORANGE, 34), wrap());
    FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(-2,-2, Gravity.BOTTOM|Gravity.LEFT); sl.setMargins(dp(24),0,0,dp(18));
    f.addView(st, sl);
    TextView mv=label("MOJAVE / 4x4", MUTED, 16);
    FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(-2,-2, Gravity.BOTTOM|Gravity.RIGHT); ml.setMargins(0,0,dp(24),dp(20));
    f.addView(mv, ml);
    return f;
  }

  // ---------- dock ----------
  View dock(){
    LinearLayout d=new LinearLayout(this); d.setOrientation(LinearLayout.HORIZONTAL);
    d.setBackground(round(PANEL, 20)); d.setGravity(Gravity.CENTER_VERTICAL);
    int p=dp(10); d.setPadding(dp(16),p,dp(20),p);
    d.addView(dockItem("HOME", true, new Runnable(){ public void run(){} }));
    d.addView(dockItem("MUSIC", false, new Runnable(){ public void run(){ open("media"); }}));
    d.addView(dockItem("4x4 TRAIL", false, new Runnable(){ public void run(){ open("pac"); }}));
    d.addView(dockItem("CAMERA", false, new Runnable(){ public void run(){ open("camera"); }}));
    d.addView(dockItem("HDMI", false, new Runnable(){ public void run(){ open("hdmi"); }}));
    View sp=new View(this); d.addView(sp, grow1());
    TextView clim=label("AUTO 72°  FAN 4", BONE, 18); d.addView(clim, wrap());
    return d;
  }

  View dockItem(String text, boolean active, final Runnable r){
    TextView t=label(text, active?0xFF07090D:BONE, 17);
    int px=dp(16), py=dp(10); t.setPadding(px,py,px,py);
    if(active) t.setBackground(round(ORANGE, 14));
    t.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ r.run(); }});
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.rightMargin=dp(8);
    t.setLayoutParams(lp);
    return t;
  }

  View tile(String head, String sub, final Runnable r){
    LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(round(PANEL2, 12)); int p=dp(14); c.setPadding(p,p,p,p);
    c.addView(label(head, BONE, 16), wrap());
    c.addView(label(sub, MUTED, 12), wrap());
    c.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ r.run(); }});
    return c;
  }

  // ---------- launching stock apps (verified activity names for 0.6.x) ----------
  void open(String which){
    String pkg=null, cls=null;
    if("carplay".equals(which)){ pkg="com.skypine.carplay"; cls="com.skypine.carplay.CarPlayActivity"; }
    else if("camera".equals(which)){ pkg="com.skypine.camera"; cls="com.skypine.camera.backcar.CameraActivity"; }
    else if("hdmi".equals(which)){ pkg="com.skypine.camera"; cls="com.skypine.camera.hdmi.HDMIActivity"; }
    else if("media".equals(which)){ pkg="com.skypine.media"; cls="com.skypine.media.ui.activity.MainActivity"; }
    else if("pac".equals(which)){ pkg="com.aampglobal.radio"; cls="com.aampglobal.radio.MainActivity"; }
    launch(pkg, cls);
  }

  void launch(String pkg, String cls){
    Intent i=null;
    try { i=getPackageManager().getLaunchIntentForPackage(pkg); } catch(Exception e){}
    if(i==null && cls!=null){ i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_DEFAULT); i.setComponent(new ComponentName(pkg,cls)); }
    if(i==null){ toast("Not found: "+pkg); return; }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try { startActivity(i); } catch(Exception e){ toast("Can't open "+pkg); }
  }

  void adminMenu(){
    final String[] o={"Set Horizon as default HOME","Open stock launcher","Android Settings","App info (uninstall)"};
    new AlertDialog.Builder(this).setTitle("Horizon v0.1").setItems(o, new DialogInterface.OnClickListener(){
      public void onClick(DialogInterface di,int w){
        if(w==0){ try{ startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }catch(Exception e){ toast("Open Settings > Home"); } }
        else if(w==1){ launch("com.skypine.mainapp","com.skypine.mainapp.ui.MainActivity"); }
        else if(w==2){ try{ startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception e){} }
        else { try{ Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.setData(Uri.parse("package:"+getPackageName())); startActivity(i);}catch(Exception e){} }
      }
    }).show();
  }

  // ---------- helpers ----------
  void tick(){
    if(clock!=null) clock.setText(new SimpleDateFormat("EEE M/d    h:mm a", Locale.US).format(new Date()).toUpperCase(Locale.US));
    h.postDelayed(new Runnable(){ public void run(){ tick(); }}, 10000);
  }
  TextView label(String s,int color,int sp){ TextView t=new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(sp); t.setTypeface(gag()); return t; }
  TextView glyph(String s,int color,int sp){ TextView t=label(s,color,sp); t.setGravity(Gravity.CENTER); return t; }
  TextView pad(TextView t){ t.setPadding(dp(14),0,0,0); return t; }
  GradientDrawable round(int color,int rad){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(rad)); return g; }
  void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
  View spacer(){ View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(12),1)); return v; }
  LinearLayout.LayoutParams row(int w,int hh){ return new LinearLayout.LayoutParams(w,hh); }
  LinearLayout.LayoutParams grow(){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0); lp.weight=1; return lp; }
  LinearLayout.LayoutParams grow1(){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2); lp.weight=1; return lp; }
  LinearLayout.LayoutParams grow1r(){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2); lp.weight=1; lp.gravity=Gravity.RIGHT; return lp; }
  LinearLayout.LayoutParams wrap(){ return new LinearLayout.LayoutParams(-2,-2); }
  LinearLayout.LayoutParams colw(int w,float wt){ LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,-1); lp.weight=wt; return lp; }
}

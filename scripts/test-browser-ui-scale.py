#!/usr/bin/env python3
"""Compile the actual scale/persistence policy with stubbed JS presentation hooks."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'web-runtime/src/main/java/mindustry/web/BrowserUiScale.java').read_text()
source=source.replace('    private BrowserUiScale(){}','''    static int prompts,shownPercent;static Action lastKeep,lastReset;static boolean reloadRequested;
    static void keepForTest(){lastKeep.run();}static void resetForTest(){lastReset.run();}
    private BrowserUiScale(){}''')
source=source.replace('private static native void showConfirmation(int percent, Action keep, Action reset);',
 '''private static void showConfirmation(int percent, Action keep, Action reset){prompts++;shownPercent=percent;lastKeep=keep;lastReset=reset;}''')
source=source.replace('private static native void reloadAfterFlush();','private static void reloadAfterFlush(){reloadRequested=true;}')
files={
 'mindustry/web/BrowserUiScale.java':source,
 'org/teavm/jso/JSObject.java':'package org.teavm.jso;public interface JSObject{}',
 'org/teavm/jso/JSFunctor.java':'package org.teavm.jso;public @interface JSFunctor{}',
 'org/teavm/jso/JSBody.java':'package org.teavm.jso;public @interface JSBody{String[] params() default {};String script();}',
 'arc/Core.java':'''package arc;import java.util.*;public class Core{
   public static Settings settings=new Settings();public static class Settings{
    public Map<String,Object> values=new HashMap<>();public int saves;
    public int getInt(String k,int d){Object v=values.get(k);return v==null?d:(Integer)v;}
    public boolean getBool(String k,boolean d){Object v=values.get(k);return v==null?d:(Boolean)v;}
    public void put(String k,Object v){values.put(k,v);}public void forceSave(){saves++;}
   }}''',
 'arc/scene/ui/layout/Scl.java':'package arc.scene.ui.layout;public class Scl{public static float product=1;public static void setProduct(float x){product=x;}}',
 'mindustry/web/ScaleTest.java':'''package mindustry.web;import arc.Core;import arc.scene.ui.layout.Scl;
 public class ScaleTest{
  static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
  public static void main(String[] args){
   Core.settings.put("inventory-sentinel",137);
   BrowserUiScale.install();check(Scl.product==1 && BrowserUiScale.prompts==0,"Default100 must not prompt");
   Core.settings.put("uiscale",125);Core.settings.put("uiscalechanged",true);BrowserUiScale.install();
   check(Scl.product==1.25f && BrowserUiScale.shownPercent==125,"Saved scale not applied before UI");
   int pending=BrowserUiScale.prompts;Core.settings.put("uiscalechanged",false);BrowserUiScale.install();
   check(BrowserUiScale.prompts==pending+1,"Reload before Keep lost pending scale confirmation");
   BrowserUiScale.keepForTest();check(Core.settings.getInt("web-uiscale-confirmed",0)==125,"Keep did not persist confirmation");
   check(!Core.settings.getBool("uiscalechanged",true) && Core.settings.saves==1,"Keep did not clear desktop countdown");
   check(!BrowserUiScale.reloadRequested,"Keep must not reload");int prompts=BrowserUiScale.prompts;
   BrowserUiScale.install();check(BrowserUiScale.prompts==prompts,"Confirmed scale re-prompts on reload");
   Core.settings.put("uiscale",150);BrowserUiScale.install();check(BrowserUiScale.prompts==prompts+1,"Changed scale without stale flag must still be recoverable");
   BrowserUiScale.resetForTest();check(Core.settings.getInt("uiscale",0)==100 && Core.settings.getInt("web-uiscale-confirmed",0)==100,"Reset did not persist100");
   check(BrowserUiScale.reloadRequested && Core.settings.saves==2,"Reset requires durable reload hook");
   check(Core.settings.getInt("inventory-sentinel",0)==137,"Scale recovery changed unrelated data");
   Core.settings.put("uiscale",-50);BrowserUiScale.install();check(Scl.product==.25f,"Minimum clamp failed");
   Core.settings.put("uiscale",1000000);BrowserUiScale.install();check(Scl.product==3f,"Maximum clamp failed");
   System.out.println("Browser scale policy: startup product, Keep persistence, repeat boot, Reset100, bounds and unrelated-data preservation PASS");
  }}'''
}
with tempfile.TemporaryDirectory() as folder:
    paths=[]
    for name,text in files.items():
        p=Path(folder)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);paths.append(str(p))
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',folder,*paths],check=True)
    subprocess.run(['java','-cp',folder,'mindustry.web.ScaleTest'],check=True)

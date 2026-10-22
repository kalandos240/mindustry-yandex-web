#!/usr/bin/env python3
"""Compile the real action boundary with controllable browser/Arc adapters."""
from pathlib import Path
import subprocess,tempfile,textwrap
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as d:
    root=Path(d)
    def write(name,body):
        p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(body))
    source=(ROOT/'web-runtime/src/main/java/mindustry/web/BrowserCampaignAssets.java').read_text()
    source=source.replace('private static native void prepare(String path, Completion done);','private static void prepare(String path, Completion done){ Harness.pending.put(path, done); Harness.calls++; }')
    source=source.replace('private static native void showFailure(String error);','private static void showFailure(String error){ Harness.errors++; }')
    write('mindustry/web/BrowserCampaignAssets.java',source)
    write('org/teavm/jso/JSObject.java','package org.teavm.jso; public interface JSObject{}')
    write('org/teavm/jso/JSFunctor.java','package org.teavm.jso; public @interface JSFunctor{}')
    write('org/teavm/jso/JSBody.java','package org.teavm.jso; public @interface JSBody{ String[] params(); String script(); }')
    write('arc/Core.java','''
      package arc;
      public class Core{
        public static Files files=new Files(); public static App app=new App();
        public static class Files{
          public java.util.Set<String> cached=new java.util.HashSet<>();
          public Fi internal(String path){ return new Fi(cached.contains(path)?128:0); }
        }
        public record Fi(long length){}
        public static class App{
          java.util.Queue<Runnable> queue=new java.util.ArrayDeque<>();
          public void post(Runnable r){queue.add(r);}
          public void drain(){while(!queue.isEmpty())queue.remove().run();}
        }
      }
    ''')
    write('mindustry/type/SectorPreset.java','''
      package mindustry.type;
      public class SectorPreset{
        public String name; public Sector sector=new Sector();
        public SectorPreset(String n){name=n;}
        public static class Sector{public Planet planet=new Planet();}
        public static class Planet{public String name="serpulo";}
      }
    ''')
    write('mindustry/web/Harness.java','''
      package mindustry.web;
      import arc.Core; import mindustry.type.SectorPreset;
      public class Harness{
        static java.util.Map<String,BrowserCampaignAssets.Completion> pending=new java.util.HashMap<>();
        static int calls,errors,runs,selection;
        static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
        static String path(String n){return "maps/serpulo/"+n+".msav";}
        static void done(String n,String error){
          if(error.isEmpty())Core.files.cached.add(path(n));
          pending.remove(path(n)).complete(error);
        }
        public static void main(String[] args){
          var a=new SectorPreset("a");var b=new SectorPreset("b");
          check(BrowserCampaignAssets.defer(a,()->selection++),"Selection must defer");
          BrowserCampaignAssets.defer(a,()->runs+=100);
          BrowserCampaignAssets.defer(a,()->runs++);
          check(calls==1,"Same map shares one request");done("a","");Core.app.drain();
          check(runs==1&&selection==0,"Go replaces selection and repeated Go runs once");
          Core.files.cached.remove(path("a"));
          BrowserCampaignAssets.defer(a,()->runs+=100);
          done("a","");
          Core.files.cached.add(path("b"));
          check(!BrowserCampaignAssets.defer(b,()->runs+=100),"Cached sector is never blocked by another map");
          Core.app.drain();check(runs==1,"Old posted completion cannot launch over the cached choice");
          Core.files.cached.clear();
          BrowserCampaignAssets.defer(a,()->runs+=100);
          BrowserCampaignAssets.defer(b,()->runs+=10);
          done("a","");Core.app.drain();check(runs==1,"Stale map cannot replace latest choice");
          done("b","");Core.app.drain();check(runs==11,"Latest uncached map launches");
          Core.files.cached.clear();
          BrowserCampaignAssets.defer(a,()->runs+=100);done("a","timeout");Core.app.drain();
          check(errors==1&&runs==11,"Failure releases the request without launching");
          BrowserCampaignAssets.defer(a,()->runs++);done("a","");Core.app.drain();
          check(runs==12,"Retry after timeout launches normally");
          System.out.println("Deferred sector actions: immediate Go, latest choice, cached escape, stale callback and retry PASS");
        }
      }
    ''')
    subprocess.run(['javac','-d',str(root/'classes')]+[str(p) for p in root.rglob('*.java')],check=True)
    subprocess.run(['java','-cp',str(root/'classes'),'mindustry.web.Harness'],check=True)

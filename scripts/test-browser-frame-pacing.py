#!/usr/bin/env python3
"""Run the production scheduler/graphics-clock methods against a deterministic rAF clock."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]

def method(source,signature):
    begin=source.index(signature);brace=source.index('{',begin);depth=1;end=brace+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[begin:end]

app=(ROOT/'web-runtime/src/main/java/mindustry/web/BrowserApplication.java').read_text()
graphics=(ROOT/'port/arc-web/src/arc/backend/web/WebGraphics.java').read_text()
code='''public class FramePacingTest{
 static class Settings{int value=240;int getInt(String key,int fallback){return value;}}
 static class Core{static Settings settings=new Settings();}
 static class Dialog{boolean isShown(){return true;}}
 static class Ui{Dialog settings=new Dialog();}
 static class State{boolean gameOver;boolean isPlaying(){return true;}}
 static class Vars{static Ui ui=new Ui();static State state=new State();}
 static class Clock{
 long frameId=-1;double lastTimestampMs=-1d,fpsWindowStartMs=-1d,maxFrameDeltaMs=50d;
 float deltaTime=1f/60f;int fps=60,fpsFrames;
 CLOCK_METHODS
 }
 static class Host{
 int frameLimit=240,frameLimitPoll,budgetFrames,budgetSlowFrames,budgetHealthyWindows;
 double nextFrameTimestamp=-1d,lastBudgetTimestamp=-1d,budgetWindowMs;
 boolean platformPaused;float renderRatioCap=1f,pixelRatioCap=1f;
 Clock graphics=new Clock();
 void markFrameLimit(int n){}void markFrameBudget(int fps,float ratio,int slow,int frames){}
 HOST_METHODS
 }
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static Host run(int cap){
   Core.settings.value=cap;Host h=new Host();int count=0;
   for(int i=0;i<=600;i++)if(h.frameDue(i*1000d/60d)){count++;h.sampleFrameBudget(i*1000d/60d);}
   int expected=cap>240?601:Math.min(cap,60)*10+1;
   check(Math.abs(count-expected)<=1,"Wrong frame count at cap "+cap+": "+count+" expected "+expected);
   if(cap==10||cap==30)check(h.renderRatioCap>=0.99f,"Intentional low FPS reduced render quality");
   return h;
 }
 public static void main(String[] args){
   for(int cap:new int[]{10,15,30,60,120,240,245})run(cap);
   Host h=run(10);Core.settings.value=60;check(h.frameDue(10001d),"Changing cap must reset its deadline");
   check(h.frameLimit==60,"Setting did not reach scheduler");
   Core.settings.value=10;h=new Host();check(h.frameDue(0),"First frame missing");
   check(h.frameDue(20000),"Resume after stall did not render");
   check(!h.frameDue(20001),"Resume must not replay a burst of overdue frames");
   Clock clock=new Clock();clock.setFrameRateLimit(10);clock.updateFrame(0);clock.updateFrame(100);
   check(Math.abs(clock.deltaTime-0.1f)<0.00001,"10 FPS simulation was clamped to half speed");
   clock.setFrameRateLimit(60);clock.updateFrame(1100);
   check(Math.abs(clock.deltaTime-0.05f)<0.00001,"Unexpected stall lost its 50ms safety bound");
   Core.settings.value=30;h=new Host();h.frameDue(0);
   for(int i=0;i<=90;i++)h.sampleFrameBudget(i*50d);
   check(h.renderRatioCap<0.99f,"Actual slow rendering below selected target did not reduce quality");
   System.out.println("Production frame pacing: 10/15/30/60/120/240/unlimited, cap change, stall, target-aware governor and real-time low-cap clock PASS");
 }
}'''
code=code.replace('CLOCK_METHODS',method(graphics,'public void updateFrame(double timestampMs)')+'\n'+method(graphics,'public void setFrameRateLimit(int limit)'))
code=code.replace('HOST_METHODS',method(app,'private boolean frameDue(double timestamp)')+'\n'+method(app,'private boolean sampleFrameBudget(double timestamp)'))
with tempfile.TemporaryDirectory() as folder:
    p=Path(folder)/'FramePacingTest.java';p.write_text(code)
    subprocess.run(['javac',str(p)],check=True)
    subprocess.run(['java','-cp',folder,'FramePacingTest'],check=True)

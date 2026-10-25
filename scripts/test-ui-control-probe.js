const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const keys={element:'name visible parent width height touchable color listeners update',group:'children',scene:'root',seq:'items size',button:'isChecked isDisabled',slider:'value min max stepSize vertical disabled',textField:'text disabled',label:'text',builder:'buffer length',dialog:'title isModal',image:'drawable',scroll:'widgetAreaBounds maxX maxY amountX amountY',color:'a',rect:'x y width height',vec:'x y',setting:'name title',contentLoader:'contentMap',mappable:'name',unlockable:'uiIcon fullIcon localizedName',regionDrawable:'region'};
const schema=Object.fromEntries(Object.entries(keys).map(([kind,list])=>[kind,Object.fromEntries(list.split(' ').map(key=>[key,key]))]));
const enabled={},disabled={},seq=items=>({items:{data:items},size:items.length});
class Actor{constructor(){Object.assign(this,{name:null,visible:1,parent:null,width:60,height:20,x:0,y:0,touchable:enabled,color:{a:1},listeners:seq([]),update:null});}}
class Group extends Actor{constructor(){super();this.children=seq([]);}}
class Button extends Group{constructor(){super();this.isChecked=0;this.isDisabled=0;}}
class Label extends Actor{constructor(text){super();this.text={$nativeString:text};}}
class Slider extends Actor{constructor(){super();Object.assign(this,{value:50,min:0,max:100,stepSize:1,vertical:0,disabled:0});}}
class TextField extends Actor{constructor(){super();this.text={$nativeString:'typed text'};this.disabled=0;}}
class ScrollPane extends Group{constructor(){super();Object.assign(this,{widgetAreaBounds:{x:0,y:0,width:100,height:50},amountX:0,amountY:0,maxX:0,maxY:300});}}
class Dialog extends Group{constructor(){super();this.title=new Label('Dialog');this.isModal=1;}}
class Image extends Actor{constructor(drawable){super();this.drawable=drawable;}}
class InputListener{$touchDown1(){return false;}}
class ClickListener extends InputListener{$clicked3(){}}
class CustomTouch extends InputListener{$touchDown1(){return true;}}
class Setting{constructor(){this.name={$nativeString:'effects'};this.title={$nativeString:'Effects'};}}
class Unlockable{constructor(name){this.name={$nativeString:name};this.localizedName={$nativeString:'Copper'};this.uiIcon={};this.fullIcon=this.uiIcon;}}
class Item extends Unlockable{} class Block extends Unlockable{} class Unit extends Unlockable{}
class RegionDrawable{constructor(region){this.region=region;}}
const add=(parent,child)=>{parent.children.items.data.push(child);parent.children.size++;child.parent=parent;return child;};
const root=new Group();root.width=1000;root.height=500;
const button=add(root,new Button());Object.assign(button,{name:{$nativeString:'test-button'},x:100,y:200,width:120,height:40});add(button,new Label('Button text'));
const setting=new Setting();button.update={captured:setting};
const unavailable=add(root,new Button());unavailable.name={$nativeString:'disabled'};unavailable.isDisabled=1;
const pane=add(root,new ScrollPane());pane.x=30;pane.y=50;pane.width=100;pane.height=50;
const slider=add(pane,new Slider());slider.y=80;
const field=add(root,new TextField());field.x=200;field.y=100;
const custom=add(root,new Actor());custom.listeners=seq([new CustomTouch()]);
const copper=new Item('copper'),contentButton=add(root,new Button());contentButton.x=400;add(contentButton,new Image(new RegionDrawable(copper.uiIcon)));
const hidden=add(root,new Group());hidden.visible=0;const fps=add(hidden,new Label('60 FPS'));fps.name={$nativeString:'web-hud-fps'};
const hover=add(root,new Actor());hover.name={$nativeString:'locked-card'};hover.listeners=seq([new ClickListener()]);
const initialRootCount=root.children.size;
const c={a_Core_scene:{root},m_Vars_content:{contentMap:{data:[seq([copper])]}},
    as_Element:Actor,asu_Label:Label,asu_Button:Button,asu_Slider:Slider,asu_TextField:TextField,asu_ScrollPane:ScrollPane,asu_Dialog:Dialog,
    ase_InputListener:InputListener,ase_ClickListener:ClickListener,ase_Touchable_disabled:disabled,
    mud_SettingsMenuDialog$SettingsTable$Setting:Setting,mc_UnlockableContent:Unlockable,mt_Item:Item,mw_Block:Block,mt_UnitType:Unit,ass_TextureRegionDrawable:RegionDrawable,
    $rt_meta:Symbol('meta'),document:{getElementById:()=>({getBoundingClientRect:()=>({left:10,top:20,right:1010,bottom:520,width:1000,height:500})})},
    as_Scene_getWidth:()=>1000,as_Scene_getHeight:()=>500,amg_Vec2__init_0:(x,y)=>({x,y}),
    as_Element_localToStageCoordinates:(actor,p)=>{for(let a=actor;a;a=a.parent){p.x+=a.x;p.y+=a.y;}return p;},
    __mindustryQaSnapshot:()=>({ready:true,menu:true,playing:false})};
c.globalThis=c;vm.createContext(c);
let helper=fs.readFileSync(__dirname+'/browser-ui-control-probe.js','utf8').replace('__UI_SCHEMA__',JSON.stringify(schema)).replace('__UI_ICONS__','[]');
vm.runInContext(helper,c);
const first=c.__mindustryUiControls({includeOffscreen:true,includeDisabled:true});
assert(first.ready && first.readOnly && first.geometryOnly && !first.truncated);
const item=first.controls.find(x=>x.name==='test-button');assert.equal(item.label,'Button text');assert.equal(item.setting.key,'effects');assert.equal(item.x,170);assert.equal(item.y,300);
assert(!first.controls.find(x=>x.name==='disabled').enabled);
assert.equal(first.controls.find(x=>x.kind==='slider').onScreen,false);
assert(first.controls.some(x=>x.kind==='inputtarget'));
assert.equal(first.controls.find(x=>x.name==='locked-card').actionable,false);assert(!c.__mindustryUiControls().controls.some(x=>x.name==='locked-card'));
assert.equal(first.controls.find(x=>x.kind==='textfield').text,'typed text');
assert(first.controls.find(x=>x.content.some(d=>d.type==='item'&&d.name==='copper')));
assert.equal(c.__mindustryUiControls().controls.find(x=>x.name==='test-button').id,item.id);
assert.equal(c.__mindustryUiActor('web-hud-fps').effectiveVisible,false);assert.equal(c.__mindustryUiActor('web-hud-fps').text,'60 FPS');
assert.equal(c.__mindustryUiActor('missing'),null);
const dialog=add(root,new Dialog());dialog.width=600;dialog.height=400;dialog.$rules9={$waveTimer2:1};
const dialogButton=add(dialog,new Button());dialogButton.x=30;add(dialogButton,new Label('Dialog action'));
const modal=c.__mindustryUiControls();assert.equal(modal.modal.title,'Dialog');assert(!modal.controls.some(x=>x.name==='test-button'));
assert(c.__mindustryUiControls({includeBackground:true}).controls.some(x=>x.name==='test-button'&&!x.inActiveModal));
assert.equal(c.__mindustryUiDialogRead('rules.waveTimer'),1);assert.throws(()=>c.__mindustryUiDialogRead('rules.unknown'));
const selected={$currentPlan:0,$block7:{$name3:{$nativeString:'ground-factory'},$plans9:seq([{$unit8:{$name3:{$nativeString:'dagger'}}}])}};
c.m_Vars_control={$input0:{$config4:{$selected2:selected,$table4:new Group()}}};
assert.equal(c.__mindustryUiConfigRead('currentPlan'),0);assert.equal(c.__mindustryUiConfigRead('block.plans.0.unit.name'),'dagger');
assert.throws(()=>c.__mindustryUiConfigRead('block.plans.7.unit.name'));
assert(c.__mindustryUiText().text.includes('Dialog action'));
assert.equal(button.isChecked,0);assert.equal(slider.value,50);assert.equal(button.x,100);assert.equal(button.y,200);assert.equal(root.children.size,initialRootCount+1);
console.log('Arc UI read-only inventory: geometry/clipping, disabled/modal/hidden states, stable IDs, setting closure, content icons, text and dialog field reads PASS');

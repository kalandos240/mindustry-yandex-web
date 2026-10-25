/* Test-only read-only inventory. It never fires listeners, changes focus or
 * values, invokes Java UI callbacks, or enables gameplay smoke branches. */
(function installUiControlProbe(){
    const F=__UI_SCHEMA__;
    const ids=new WeakMap();let nextId=1;
    const get=(object,kind,name)=>object?.[F[kind]?.[name]];
    const children=actor=>{
        const seq=get(actor,'group','children');
        const items=get(seq,'seq','items');
        const data=items?.data || items;
        return data ? Array.from(data).slice(0,Math.max(0,Math.min(get(seq,'seq','size') || 0,15000))) : [];
    };
    const str=value=>{
        if(value==null)return '';
        if(typeof value==='string')return value;
        if(typeof value.$nativeString==='string')return value.$nativeString;
        const buffer=get(value,'builder','buffer')?.data;
        const length=Math.min(get(value,'builder','length') || 0,4096);
        if(!buffer)return '';
        let text='';for(let i=0;i<length;i+=512)text+=String.fromCharCode(...Array.from(buffer).slice(i,Math.min(length,i+512)));
        return text;
    };
    const type=actor=>actor?.constructor?.[$rt_meta]?.name || actor?.constructor?.name || 'Element';
    const name=actor=>str(get(actor,'element','name'));
    const is=(actor,cls)=>typeof cls==='function' && actor instanceof cls;
    const purePoint=(actor,x,y)=>{
        const value=as_Element_localToStageCoordinates(actor,amg_Vec2__init_0(x,y));
        return {x:get(value,'vec','x'),y:get(value,'vec','y')};
    };
    const rect=(actor,x=0,y=0,width=get(actor,'element','width'),height=get(actor,'element','height'))=>{
        const points=[[x,y],[x+width,y],[x,y+height],[x+width,y+height]].map(p=>purePoint(actor,...p));
        const xs=points.map(p=>p.x),ys=points.map(p=>p.y);
        return {left:Math.min(...xs),bottom:Math.min(...ys),right:Math.max(...xs),top:Math.max(...ys)};
    };
    const intersect=(a,b)=>({left:Math.max(a.left,b.left),bottom:Math.max(a.bottom,b.bottom),right:Math.min(a.right,b.right),top:Math.min(a.top,b.top)});
    const hasArea=r=>Number.isFinite(r.left+r.bottom+r.right+r.top) && r.right>r.left && r.top>r.bottom;
    function labels(actor,depth=0,limit=320){
        if(!actor || depth>4 || limit<=0)return '';
        let value=is(actor,asu_Label)?str(get(actor,'label','text')):'';
        for(const child of children(actor)){
            if(get(child,'element','visible'))value+=' '+labels(child,depth+1,limit-value.length);
            if(value.length>=limit)break;
        }
        return value.replace(/\s+/g,' ').trim().slice(0,limit);
    }
    function descendants(actor,parent){
        for(let at=actor;at;at=get(at,'element','parent'))if(at===parent)return true;
        return false;
    }
    function disabledByHierarchy(actor){
        for(let at=actor;at;at=get(at,'element','parent'))if(get(at,'element','touchable')===ase_Touchable_disabled)return true;
        return false;
    }
    function hasClickListener(actor){
        const seq=get(actor,'element','listeners'),items=get(seq,'seq','items')?.data || [];
        const count=Math.min(get(seq,'seq','size') || 0,items.length);
        for(let i=0;i<count;i++)if(is(items[i],ase_ClickListener))return true;
        return false;
    }
    function hasTouchDownListener(actor){
        const seq=get(actor,'element','listeners'),items=get(seq,'seq','items')?.data || [];
        const count=Math.min(get(seq,'seq','size') || 0,items.length);
        for(let i=0;i<count;i++){
            const listener=items[i];
            if(is(listener,ase_InputListener) && typeof listener.$touchDown1==='function' &&
                listener.$touchDown1!==ase_InputListener.prototype.$touchDown1)return true;
        }
        return false;
    }
    function settingFor(actor){
        const seq=get(actor,'element','listeners'),items=get(seq,'seq','items')?.data || [];
        const roots=[get(actor,'element','update'),...Array.from(items).slice(0,get(seq,'seq','size') || 0)];
        const seen=new Set();let queue=roots.filter(Boolean).map(value=>({value,depth:0}));
        while(queue.length && seen.size<160){
            const {value,depth}=queue.shift();
            if(!value || typeof value!=='object' || seen.has(value))continue;
            seen.add(value);
            if(is(value,mud_SettingsMenuDialog$SettingsTable$Setting))return {
                key:str(get(value,'setting','name')),title:str(get(value,'setting','title')),type:type(value)};
            // Never walk back into the Scene or a settings table through a
            // callback's captured actor. Only inspect callback/data references.
            if(depth>=4 || is(value,as_Element) || value.$nativeString!=null)continue;
            for(const child of Object.values(value)){
                if(child && typeof child==='object'){
                    if(Array.isArray(child))queue.push(...child.slice(0,16).map(value=>({value,depth:depth+1})));
                    else queue.push({value:child,depth:depth+1});
                }
            }
        }
        return null;
    }
    const dialogTitle=dialog=>{
        const title=get(dialog,'dialog','title');
        return str(title) || labels(title);
    };
    function activeDialog(){
        let result=null,count=0;const seen=new Set();
        function visit(actor,visible=true){
            if(!actor || seen.has(actor) || ++count>15000)return;
            seen.add(actor);visible=visible && !!get(actor,'element','visible');
            const alpha=get(get(actor,'element','color'),'color','a');
            visible=visible && (alpha==null || alpha>0.001);
            if(!visible)return;
            if(is(actor,asu_Dialog) && get(actor,'dialog','isModal'))result=actor;
            for(const child of children(actor))visit(child,visible);
        }
        visit(get(a_Core_scene,'scene','root'));return result;
    }
    function rawField(object,name){
        if(object==null)throw Error('UI read target is null: '+name);
        const keys=Object.keys(object).filter(key=>key==='$'+name || key.startsWith('$'+name) && /^\d+$/.test(key.slice(name.length+1)));
        if(keys.length!==1)throw Error('UI field missing or ambiguous: '+name+' ('+keys.join(',')+')');
        return object[keys[0]];
    }
    function plain(value){
        if(value==null || ['string','number','boolean'].includes(typeof value))return value;
        if(typeof value==='bigint')return String(value);
        if(typeof value.$nativeString==='string')return value.$nativeString;
        if(value.constructor?.name==='jl_Boolean')return !!rawField(value,'value');
        if(/^jl_(?:Integer|Long|Float|Double|Short|Byte)$/.test(value.constructor?.name || ''))return plain(rawField(value,'value'));
        return {kind:'object',type:value.constructor?.name || 'Object'};
    }
    globalThis.__mindustryUiDialogRead=function(path){
        let value=activeDialog();if(!value)throw Error('No active UI dialog');
        for(const key of String(path).split('.')){
            if(!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key))throw Error('Invalid UI field path');
            value=rawField(value,key);
        }
        return plain(value);
    };
    globalThis.__mindustryUiText=function(options={}){
        const dialog=activeDialog(),root=dialog || get(a_Core_scene,'scene','root');
        const width=as_Scene_getWidth(a_Core_scene),height=as_Scene_getHeight(a_Core_scene);
        const out=[],seen=new Set();let count=0;
        function visit(actor,visible=true){
            if(!actor || seen.has(actor) || ++count>15000)return;
            seen.add(actor);visible=visible && !!get(actor,'element','visible');
            const alpha=get(get(actor,'element','color'),'color','a');
            visible=visible && (alpha==null || alpha>0.001);if(!visible)return;
            if(is(actor,asu_Label)){
                let clipped=intersect(rect(actor),{left:0,bottom:0,right:width,top:height});
                for(let parent=get(actor,'element','parent');parent;parent=get(parent,'element','parent')){
                    if(is(parent,asu_ScrollPane)){
                        const area=get(parent,'scroll','widgetAreaBounds');
                        if(area)clipped=intersect(clipped,rect(parent,get(area,'rect','x'),get(area,'rect','y'),get(area,'rect','width'),get(area,'rect','height')));
                    }
                }
                const text=str(get(actor,'label','text')),onScreen=hasArea(clipped);
                if(text && (onScreen || options.includeOffscreen))out.push({name:name(actor),text,onScreen});
            }
            for(const child of children(actor))visit(child,visible);
        }
        visit(root);
        return {dialog:dialog?{type:type(dialog),title:dialogTitle(dialog)}:null,
            text:out.map(item=>item.text).join('\n'),labels:out,truncated:count>15000};
    };
    function kind(actor){
        if(is(actor,asu_Slider))return 'slider';
        if(is(actor,asu_TextField))return 'textfield';
        if(is(actor,asu_Button))return 'button';
        if(is(actor,asu_ScrollPane))return 'scrollpane';
        if(hasClickListener(actor))return 'clickable';
        if(hasTouchDownListener(actor))return 'inputtarget';
        return null;
    }
    globalThis.__mindustryUiActor=function(wanted){
        const scene=a_Core_scene,root=get(scene,'scene','root');
        if(!root)return null;
        let found=null,count=0;const visited=new Set();
        function walk(actor,visible=true){
            if(!actor || found || visited.has(actor) || ++count>15000)return;
            visited.add(actor);
            visible=visible && !!get(actor,'element','visible');
            const alpha=get(get(actor,'element','color'),'color','a');
            visible=visible && (alpha==null || alpha>0.001);
            if(name(actor)===wanted){found={actor,visible};return;}
            for(const child of children(actor))walk(child,visible);
        }
        walk(root);
        if(!found)return null;
        const actor=found.actor,stageRect=rect(actor);
        const width=as_Scene_getWidth(scene),height=as_Scene_getHeight(scene);
        const clipped=intersect(stageRect,{left:0,bottom:0,right:width,top:height});
        const bounds=document.getElementById('mindustry-canvas')?.getBoundingClientRect();
        return {name:wanted,type:type(actor),text:labels(actor,0,1600),
            visible:!!get(actor,'element','visible'),effectiveVisible:found.visible,
            onScreen:found.visible && hasArea(clipped),stageRect,
            cssRect:bounds?{left:bounds.left+stageRect.left/width*bounds.width,top:bounds.bottom-stageRect.top/height*bounds.height,
                right:bounds.left+stageRect.right/width*bounds.width,bottom:bounds.bottom-stageRect.bottom/height*bounds.height}:null};
    };
    globalThis.__mindustryUiControls=function(options={}){
        const scene=a_Core_scene,root=get(scene,'scene','root');
        if(!root)return {ready:false,controls:[],truncated:false};
        const width=as_Scene_getWidth(scene),height=as_Scene_getHeight(scene);
        const canvas=document.getElementById('mindustry-canvas'),bounds=canvas?.getBoundingClientRect();
        const icons=new Map(__UI_ICONS__.filter(([value])=>value));
        const contentIcons=new Map();
        const registry=get(m_Vars_content,'contentLoader','contentMap')?.data || [];
        for(const seq of registry){
            const items=get(seq,'seq','items')?.data || [];
            for(const content of Array.from(items).slice(0,Math.min(get(seq,'seq','size') || 0,3000))){
                if(!is(content,mc_UnlockableContent))continue;
                const descriptor={type:is(content,mt_Item)?'item':is(content,mw_Block)?'block':is(content,mt_UnitType)?'unit':type(content),
                    name:str(get(content,'mappable','name')),localizedName:str(get(content,'unlockable','localizedName'))};
                for(const field of ['uiIcon','fullIcon']){
                    const region=get(content,'unlockable',field);if(!region)continue;
                    if(!contentIcons.has(region))contentIcons.set(region,[]);
                    const list=contentIcons.get(region);if(!list.some(c=>c.type===descriptor.type && c.name===descriptor.name))list.push(descriptor);
                }
            }
        }
        const actorIcons=actor=>{
            const names=new Set();
            const collect=(at,depth)=>{
                if(depth>4)return;
                const drawable=get(at,'image','drawable');
                if(icons.has(drawable))names.add(icons.get(drawable));
                for(const child of children(at))collect(child,depth+1);
            };
            collect(actor,0);return Array.from(names);
        };
        const actorContent=actor=>{
            const found=new Map();
            const collect=(at,depth)=>{
                if(depth>4)return;
                const drawable=get(at,'image','drawable');
                const region=is(drawable,ass_TextureRegionDrawable)?get(drawable,'regionDrawable','region'):null;
                for(const content of contentIcons.get(region) || [])found.set(content.type+'/'+content.name,content);
                for(const child of children(at))collect(child,depth+1);
            };
            collect(actor,0);return Array.from(found.values());
        };
        let nodes=0,truncated=false,modal=null;const visited=new Set();
        const actors=[];
        function walk(actor,path,visible=true){
            if(!actor || visited.has(actor))return;
            visited.add(actor);
            if(++nodes>15000){truncated=true;return;}
            visible=visible && !!get(actor,'element','visible');
            const alpha=get(get(actor,'element','color'),'color','a');
            visible=visible && (alpha==null || alpha>0.001);
            if(!visible && !options.includeHidden)return;
            if(visible && is(actor,asu_Dialog) && get(actor,'dialog','isModal'))modal=actor;
            actors.push({actor,path,visible});
            children(actor).forEach((child,index)=>walk(child,path+'.'+index,visible));
        }
        walk(root,'0');
        const toCss=r=>bounds?{left:bounds.left+r.left/width*bounds.width,top:bounds.bottom-r.top/height*bounds.height,
            right:bounds.left+r.right/width*bounds.width,bottom:bounds.bottom-r.bottom/height*bounds.height}:null;
        const controls=[];
        for(const entry of actors){
            const actor=entry.actor,k=kind(actor);if(!k)continue;
            const full=rect(actor);let clipped=intersect(full,{left:0,bottom:0,right:width,top:height});
            let dialog=null;const parentNames=[];
            for(let parent=get(actor,'element','parent');parent;parent=get(parent,'element','parent')){
                if(name(parent))parentNames.push(name(parent));
                if(!dialog && is(parent,asu_Dialog))dialog=parent;
                if(is(parent,asu_ScrollPane)){
                    const area=get(parent,'scroll','widgetAreaBounds');
                    if(area)clipped=intersect(clipped,rect(parent,get(area,'rect','x'),get(area,'rect','y'),get(area,'rect','width'),get(area,'rect','height')));
                }
            }
            const inActiveModal=!modal || descendants(actor,modal);
            const onScreen=entry.visible && hasArea(clipped);
            if(!options.includeOffscreen && !onScreen)continue;
            if(!options.includeBackground && !inActiveModal)continue;
            let disabled=disabledByHierarchy(actor);
            if(k==='button')disabled=disabled || !!get(actor,'button','isDisabled');
            if(k==='slider')disabled=disabled || !!get(actor,'slider','disabled');
            if(k==='textfield')disabled=disabled || !!get(actor,'textField','disabled');
            if(!options.includeDisabled && disabled)continue;
            if(!ids.has(actor))ids.set(actor,'ui-'+nextId++);
            const label=k==='textfield'?str(get(actor,'textField','text')):labels(actor);
            const item={id:ids.get(actor),path:entry.path,kind:k,type:type(actor),name:name(actor),label,
                nearbyLabels:labels(get(actor,'element','parent'),0,500),icons:actorIcons(actor),content:actorContent(actor),parentNames,
                dialog:dialog?dialogTitle(dialog):'',setting:settingFor(actor),visible:entry.visible,enabled:!disabled,onScreen,inActiveModal,
                hitTested:false,stageRect:full,visibleStageRect:clipped,cssRect:toCss(full),visibleCssRect:toCss(clipped)};
            if(onScreen){item.stageX=(clipped.left+clipped.right)/2;item.stageY=(clipped.bottom+clipped.top)/2;
                if(bounds){item.x=bounds.left+item.stageX/width*bounds.width;item.y=bounds.bottom-item.stageY/height*bounds.height;}}
            if(k==='button')item.checked=!!get(actor,'button','isChecked');
            if(k==='slider')Object.assign(item,{value:get(actor,'slider','value'),min:get(actor,'slider','min'),max:get(actor,'slider','max'),step:get(actor,'slider','stepSize'),vertical:!!get(actor,'slider','vertical')});
            if(k==='scrollpane')Object.assign(item,{scrollX:get(actor,'scroll','amountX'),scrollY:get(actor,'scroll','amountY'),maxX:get(actor,'scroll','maxX'),maxY:get(actor,'scroll','maxY')});
            controls.push(item);
        }
        return {ready:true,readOnly:true,geometryOnly:true,truncated,nodes,stage:{width,height},
            modal:modal?{type:type(modal),name:name(modal),title:dialogTitle(modal)}:null,
            game:typeof globalThis.__mindustryQaSnapshot==='function'?globalThis.__mindustryQaSnapshot():null,
            controls};
    };
})();

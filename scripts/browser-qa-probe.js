/* Mindustry QA read-only state probe. Test builds only. */
(function installMindustryQaProbe(){
    function field(object, name){
        if(object == null) throw new Error('QA read target is null: '+name);
        const names=Object.keys(object).filter(key=>key==='$'+name ||
            key.startsWith('$'+name) && /^\d+$/.test(key.slice(name.length+1)));
        if(names.length!==1) throw new Error('QA field is missing or ambiguous: '+name+' ('+names.join(',')+')');
        return object[names[0]];
    }
    function plain(value){
        if(value==null || ['string','number','boolean'].includes(typeof value)) return value;
        if(typeof value==='bigint') return String(value);
        if(typeof value.$nativeString==='string') return value.$nativeString;
        if(value.constructor?.name==='jl_Boolean') return !!field(value,'value');
        if(/^jl_(?:Integer|Long|Float|Double|Short|Byte)$/.test(value.constructor?.name || '')) return plain(field(value,'value'));
        return {kind:'object',type:value.constructor?.name || 'Object'};
    }
    globalThis.__mindustryQaSnapshot=function(){
        const state=m_Vars_state;
        if(state==null) return {ready:false};
        return {
            ready:true,
            playing:!!mc_GameState_isPlaying(state),
            paused:!!mc_GameState_isPaused(state),
            menu:!!mc_GameState_isMenu(state),
            campaign:!!mc_GameState_isCampaign(state),
            campaignActive:!!mw_Campaign_active(),
            tick:Number(state.$tick),
            updateId:String(state.$updateId),
            wave:Number(state.$wave)
        };
    };
    globalThis.__mindustryQaSetting=function(name){
        const key=$rt_str(String(name));
        return plain(a_Settings_get(a_Core_settings,key,as_ObjectMap_get(field(a_Core_settings,'defaults'),key)));
    };
    globalThis.__mindustryQaRead=function(target, path){
        const targets={renderer:m_Vars_renderer,state:m_Vars_state,control:m_Vars_control,ui:m_Vars_ui,
            audio:a_Core_audio,camera:a_Core_camera,settings:a_Core_settings,graphics:a_Core_graphics,input:a_Core_input};
        if(!Object.prototype.hasOwnProperty.call(targets,target)) throw new Error('Unknown QA target: '+target);
        let value=targets[target];
        for(const name of String(path).split('.')){
            if(!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) throw new Error('Invalid QA field path');
            value=field(value,name);
        }
        return plain(value);
    };
    globalThis.__mindustryQaUiScale=function(){
        if(typeof asul_Scl_product==='undefined') throw new Error('QA native UI scale unavailable');
        return Number(asul_Scl_product);
    };
    globalThis.__mindustryQaKeyDown=function(name){
        const keys={a:ai_KeyCode_a,d:ai_KeyCode_d,w:ai_KeyCode_w,s:ai_KeyCode_s,
            space:ai_KeyCode_space,escape:ai_KeyCode_escape,left:ai_KeyCode_left,right:ai_KeyCode_right,
            up:ai_KeyCode_up,down:ai_KeyCode_down,mouseleft:ai_KeyCode_mouseLeft,mouseright:ai_KeyCode_mouseRight};
        const key=keys[String(name).toLowerCase()];
        if(key==null) throw new Error('Unknown or uninitialized QA key: '+name);
        return !!a_Input_keyDown0(a_Core_input,key);
    };
})();

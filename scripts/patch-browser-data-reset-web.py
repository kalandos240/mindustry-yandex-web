"""Keep confirmed browser Data resets durable and safe against active-world autosave."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'work/Mindustry/core/src/mindustry/ui/dialogs/SettingsMenuDialog.java'
source=path.read_text()
actions=['clearplanetresearch','clearplanetcampaignsaves','cleardata','clearsaves','clearresearch','clearcampaignsaves']
def closing_brace(text,start):
    depth=0;quoted=False;escape=False
    for index in range(start,len(text)):
        char=text[index]
        if quoted:
            if escape:escape=False
            elif char=='\\':escape=True
            elif char=='"':quoted=False
        elif char=='"':quoted=True
        elif char=='{':depth+=1
        elif char=='}':
            depth-=1
            if depth==0:return index
    raise SystemExit('Unclosed confirmed Data action')
for action in actions:
    anchor='t.button("@settings.'+action+'",'
    if source.count(anchor)!=1:raise SystemExit('Pinned Data button changed: '+action)
    start=source.index(anchor);confirm=source.index('ui.showConfirm(',start)
    body=source.index('() -> {',confirm)+len('() -> ')
    end=closing_brace(source,body)
    guard='\n                    if(state.isGame()) return;'
    source=source[:body+1]+guard+source[body+1:]
    end+=len(guard)
    source=source[:end]+'    webMarkDataReset();\n                '+source[end:]
    margin=source.index('.marginLeft(4)',end)
    if margin-end>250:raise SystemExit('Pinned Data action margin changed: '+action)
    source=source[:margin]+'.disabled(b -> state.isGame())'+source[margin:]
for dialog in ['dataDialog','planetDataDialog']:
    anchor='        '+dialog+'.addCloseButton();'
    if source.count(anchor)!=1:raise SystemExit('Pinned Data dialog changed: '+dialog)
    source=source.replace(anchor,anchor+'\n        '+dialog+'.cont.add("@web.data.resetmenu").wrap().width(280f).visible(() -> state.isGame()).row();',1)
anchor='    String getLogs(){'
if source.count(anchor)!=1:raise SystemExit('Pinned Settings getLogs anchor changed')
source=source.replace(anchor,'''    private void webMarkDataReset(){
        // Executed only inside the existing native confirmation callback.
        settings.put("web-campaign-reset-at", System.currentTimeMillis());
        settings.forceSave();
    }

'''+anchor,1)
assert source.count('webMarkDataReset();')==6
path.write_text(source)
for name,message in [('bundle.properties','Return to the main menu before resetting game data.'),
                     ('bundle_ru.properties','Вернитесь в главное меню перед сбросом игровых данных.')]:
    bundle=ROOT/'work/Mindustry/core/assets/bundles'/name
    text=bundle.read_text();key='web.data.resetmenu = '
    if key not in text:bundle.write_text(text+'\n'+key+message+'\n')
print('Confirmed Data resets: menu-only guards, durable reset marker and native forceSave')

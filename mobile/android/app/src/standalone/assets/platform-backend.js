(function(){
  var pending=new Map(), sequence=0;

  function call(method,args){
    return new Promise(function(resolve,reject){
      if(!window.VitrNative||typeof window.VitrNative[method]!=="function"){
        reject(new Error("Vitr Android backend is unavailable."));
        return;
      }
      var id="vitr-"+Date.now()+"-"+(++sequence);
      pending.set(id,{resolve:resolve,reject:reject});
      try{
        window.VitrNative[method].apply(window.VitrNative,(args||[]).concat(id));
      }catch(error){
        pending.delete(id);
        reject(error);
      }
    });
  }

  window.__vitrNativeResult=function(id,ok,data){
    var entry=pending.get(id);
    if(!entry)return;
    pending.delete(id);
    if(ok)entry.resolve(data);
    else entry.reject(new Error(data&&data.message||"Vitr Android backend error"));
  };

  window.__vitrNativePlayerState=function(state){
    window.dispatchEvent(new CustomEvent("vitr-player-state",{detail:state||{}}));
  };

  window.vitrPlatformBackend={
    search:function(query){return call("search",[String(query||"")])},
    play:function(track){return call("play",[JSON.stringify(track||{})])},
    state:function(){return call("state",[])},
    togglePlay:function(){window.VitrNative&&window.VitrNative.togglePlay()},
    pause:function(){window.VitrNative&&window.VitrNative.pause()},
    seek:function(positionMs){window.VitrNative&&window.VitrNative.seek(Number(positionMs)||0)}
  };

  window.dispatchEvent(new CustomEvent("vitr-backend-ready"));
})();

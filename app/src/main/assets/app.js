const $=id=>document.getElementById(id);
const DB=(window.REEL_FIREBASE_CONFIG?.databaseURL||"").replace(/\/$/,"");
const CONTROL_PATH="/configuracoes/reelSeries";
const USERS_PATH="/usuarios",SESSION_KEY="reel_user_phone",DAY=86400000;function phoneUid(v){return String(v||"").replace(/\D/g,"")}function userPath(p){return `${USERS_PATH}/${phoneUid(p)}`}function expiryMs(u){let v=u?.expiresAt;if(typeof v==="number")return v;let n=Date.parse(v||"");return Number.isFinite(n)?n:0}function daysLeft(u){return Math.max(0,Math.ceil((expiryMs(u)-Date.now())/DAY))}function showLogin(m=""){$("planGate")?.classList.remove("show");$("loginGate")?.classList.add("show");if($("loginMsg"))$("loginMsg").textContent=m}function showPlan(u,r){$("loginGate")?.classList.remove("show");$("planGate")?.classList.add("show");$("planTitle").textContent=r==="blocked"?"Acesso bloqueado":"Plano vencido";$("planText").textContent=r==="blocked"?"Seu usuário foi bloqueado pelo administrador.":`Olá, ${u?.name||"usuário"}. Seu plano venceu.`}async function getCurrentUser(){
  let p=localStorage.getItem(SESSION_KEY);
  if(!p)return null;
  try{
    let remote=await rest(userPath(p));
    if(remote){
      localStorage.setItem("reel_local_user_"+p,JSON.stringify(remote));
      localStorage.removeItem("reel_pending_user");
      return remote;
    }
  }catch(e){console.warn("Usando cadastro local",e)}
  try{return JSON.parse(localStorage.getItem("reel_local_user_"+p)||"null")}catch{return null}
}async function loginOrRegister(){
  const btn=$("loginBtn");
  let name=$("loginName").value.trim(),phone=phoneUid($("loginPhone").value);
  if(name.length<2)return showLogin("Digite seu nome completo.");
  if(phone.length<10)return showLogin("Digite o telefone com DDD.");
  btn.disabled=true;btn.textContent="Entrando...";
  $("loginMsg").textContent="Verificando cadastro...";
  let u=null,online=true;
  try{
    u=await rest(userPath(phone));
  }catch(e){
    online=false;
    console.warn("Falha ao consultar usuário no Firebase",e);
  }
  try{
    if(!u){
      let d=Number(S.control?.defaultPlanDays||30);
      u={uid:phone,phone,name,blocked:false,createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),expiresAt:Date.now()+d*DAY,planDays:d};
      if(online){
        $("loginMsg").textContent="Criando cadastro...";
        await rest(userPath(phone),"PUT",u);
      }else{
        u.pendingSync=true;
        localStorage.setItem("reel_pending_user",JSON.stringify(u));
      }
    }else{
      u={...u,uid:phone,phone,name,updatedAt:new Date().toISOString()};
      if(online)await rest(userPath(phone),"PATCH",{uid:phone,phone,name,updatedAt:u.updatedAt});
    }
    localStorage.setItem(SESSION_KEY,phone);
    localStorage.setItem("reel_local_user_"+phone,JSON.stringify(u));
    $("loginMsg").textContent=online?"Cadastro confirmado. Abrindo...":"Firebase indisponível. Abrindo no modo local...";
    await validateUserAndLoad();
  }catch(e){
    console.error(e);
    $("loginMsg").textContent="Não foi possível salvar no Firebase: "+(e.message||"erro desconhecido");
  }finally{
    btn.disabled=false;btn.textContent="Entrar / Cadastrar";
  }
}
async function syncPendingUser(){
  try{
    let p=JSON.parse(localStorage.getItem("reel_pending_user")||"null");
    if(!p)return;
    let clean={...p};delete clean.pendingSync;
    await rest(userPath(clean.phone),"PUT",clean);
    localStorage.setItem("reel_local_user_"+clean.phone,JSON.stringify(clean));
    localStorage.removeItem("reel_pending_user");
  }catch(e){console.warn("Cadastro continua pendente de sincronização",e)}
}
async function validateUserAndLoad(){let u;try{u=await getCurrentUser()}catch{}if(!u)return showLogin();window.REEL_CURRENT_USER=u;if(u.blocked)return showPlan(u,"blocked");if(expiryMs(u)<=Date.now())return showPlan(u,"expired");$("loginGate")?.classList.remove("show");$("planGate")?.classList.remove("show");await load()}function renewWhatsApp(){getControl().then(c=>{let u=window.REEL_CURRENT_USER,n=phoneUid(c.renewWhatsapp||"5534998607006"),t=encodeURIComponent(`Olá! Quero renovar meu plano Reel Séries.\nNome: ${u?.name||""}\nTelefone/UID: ${u?.phone||""}`);window.open(`https://wa.me/${n}?text=${t}`,"_blank","noopener")})}

const S={all:[],filtered:[],visible:36,view:"inicio",cat:"Todos",current:null,featured:null,wide:false,source:"",fav:new Set(JSON.parse(localStorage.getItem("rs_fav")||"[]")),hist:JSON.parse(localStorage.getItem("rs_hist")||"{}"),hls:null,control:null};
const safe=v=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const norm=v=>String(v||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase();
const title=i=>i.nome||i.Nome||i.titulo||i.title||"Sem título";
const rawUrl=i=>i.url||i.Link||i.link||i.video||i.stream||"";
const validUrl=u=>/^(https?:\/\/|blob:|data:)/i.test(String(u||"").trim());
const url=i=>validUrl(rawUrl(i))?String(rawUrl(i)).trim():"";
const poster=i=>i.capa||i.Capa||i.poster||i.capaFundo||i["Capa de fundo"]||"icons/placeholder.svg";
const backdrop=i=>i.capaFundo||i["Capa de fundo"]||i.backdrop||poster(i);
const desc=i=>i.sinopse||i.Sinopse||"Sinopse não informada.";
const genre=i=>i["gênero"]||i.genero||i.Categoria||i.categoria||"";
const type=i=>i.tipo||i.Tipo||i.__category||"Conteúdo";
const ep=i=>i.Episodio||i.episodio||i.episode||"";
const season=i=>{let x=String(ep(i)).match(/(\d+)\s*x\s*\d+/i);return x?+x[1]:+(i.temporada||i.season||1)};
const epnum=i=>{let x=String(ep(i)).match(/\d+\s*x\s*(\d+)/i);return x?+x[1]:+(i.numeroEpisodio||0)};
const year=i=>i.ano||i.Ano||String(i.dataLancamento||i["Data de Lançamento"]||"").match(/\d{4}/)?.[0]||"";
const rating=i=>i.imdb||i.Imdb||"";
const uid=i=>i.uid||i.UID||i.__key||btoa(unescape(encodeURIComponent(title(i)))).slice(0,22);
function contentKind(i){const t=norm([type(i),i.__category,genre(i),ep(i),i.temporadas].join(" "));return(t.includes("serie")||t.includes("novela")||t.includes("anime")||t.includes("dorama")||ep(i))?"serie":"filme"}
function isMedia(o){if(!o||typeof o!=="object"||Array.isArray(o))return false;let k=Object.keys(o).map(norm);return k.some(x=>["nome","titulo","title","episodio","episode"].includes(x))&&k.some(x=>["url","link","video","stream","capa","poster","sinopse","tipo"].includes(x))}
function readable(v){return String(v||"").replace(/^[-_]+/,"").replace(/([a-zá-ú])([A-ZÁ-Ú])/g,"$1 $2").replace(/[_-]+/g," ").replace(/\s+/g," ").trim()||"Série"}
function flatten(node,path=[],out=[]){if(!node||typeof node!=="object")return out;if(isMedia(node)){let x={...node,__path:"/"+path.join("/"),__key:path.at(-1)||"",__category:path[0]||"Conteúdo"};if(!x.nome&&!x.Nome&&!x.titulo&&!x.title)x.nome=readable(path[0]);out.push(x);return out}for(const [k,v] of Object.entries(node)){if(path.length===0&&norm(k)==="configuracoes")continue;flatten(v,[...path,k],out)}return out}
function dedupe(a){let m=new Map;for(const i of a){let k=(uid(i)||i.__path)+"|"+ep(i);if(!m.has(k))m.set(k,i)}return[...m.values()]}
function restUrl(path){path="/"+String(path||"").replace(/^\/+|\/+$/g,"");return DB+path+".json"}
async function rest(path,opts={}){const ctrl=new AbortController();const timer=setTimeout(()=>ctrl.abort(),12000);try{let r=await fetch(restUrl(path),{cache:"no-store",...opts,signal:ctrl.signal});if(!r.ok)throw new Error("Firebase respondeu "+r.status);return r.status===204?null:await r.json()}finally{clearTimeout(timer)}}
async function getControl(){try{let c=await rest(CONTROL_PATH);return c||{appEnabled:true,firebaseEnabled:true,localEnabled:true,preferredSource:"firebase",autoFallback:true,defaultPlanDays:30,renewWhatsapp:"5534998607006"}}catch(e){return {appEnabled:true,firebaseEnabled:false,localEnabled:true,preferredSource:"local",autoFallback:true,offlineControl:true}}}
async function loadFirebase(){let root=localStorage.getItem("rs_path")||"/";return rest(root)}
async function loadLocal(){for(const p of ["Firebase_Completo.json","dados/Firebase_Completo.json"]){try{let r=await fetch(p,{cache:"no-store"});if(r.ok)return r.json()}catch{}}throw new Error("Arquivo interno não encontrado")}
function lock(msg){$("accessLock").classList.add("show");$("lockText").textContent=msg;$("splash").classList.add("hide")}
function unlock(){$("accessLock").classList.remove("show")}
async function load(force){$("splash").classList.remove("hide");$("splashMsg").textContent="Verificando liberação do painel...";S.control=await getControl();if(!S.control.appEnabled)return lock("O aplicativo foi bloqueado no painel administrativo.");unlock();let source=force||S.control.preferredSource||"firebase";if(source==="firebase"&&!S.control.firebaseEnabled)source=S.control.localEnabled?"local":"blocked";if(source==="local"&&!S.control.localEnabled)source=S.control.firebaseEnabled?"firebase":"blocked";if(source==="blocked")return lock("Nenhuma fonte de catálogo foi liberada no painel.");let data;try{if(source==="firebase"){data=await loadFirebase();S.source="Firebase"}else{data=await loadLocal();S.source="Arquivo interno"}}catch(e){if(S.control.autoFallback){if(source==="firebase"&&S.control.localEnabled){data=await loadLocal();S.source="Arquivo interno (reserva)"}else if(source==="local"&&S.control.firebaseEnabled){data=await loadFirebase();S.source="Firebase (reserva)"}else throw e}else throw e}S.all=dedupe(flatten(data)).filter(x=>title(x)!=="Sem título");S.all.sort((a,b)=>title(a).localeCompare(title(b),"pt-BR"));chooseFeatured();chips();view(S.view);$("sourceBox").textContent=`Fonte atual: ${S.source} • ${S.all.length.toLocaleString("pt-BR")} itens`;setTimeout(()=>$("splash").classList.add("hide"),350)}
function family(i){let key=norm(title(i));return S.all.filter(x=>norm(title(x))===key||norm(x.__category)===norm(i.__category))}
function chooseFeatured(){S.featured=S.all.find(x=>backdrop(x)&&desc(x))||S.all[0];if(!S.featured)return;$("heroBg").style.backgroundImage=`url("${backdrop(S.featured).replace(/"/g,"%22")}")`;$("heroTitle").textContent=title(S.featured);$("heroDesc").textContent=desc(S.featured);$("heroMeta").textContent=[year(S.featured),genre(S.featured),rating(S.featured)&&"★ "+rating(S.featured)].filter(Boolean).join(" • ")}
function chips(){let cats=["Todos",...new Set(S.all.map(x=>x.__category).filter(Boolean))].slice(0,18);$("chips").innerHTML=cats.map(c=>`<button data-cat="${safe(c)}" class="${S.cat===c?"active":""}">${safe(readable(c))}</button>`).join("")}
function filterItems(){let a=S.all;if(S.view==="filmes")a=a.filter(x=>contentKind(x)==="filme");if(S.view==="series")a=a.filter(x=>contentKind(x)==="serie");if(S.view==="favoritos")a=a.filter(x=>S.fav.has(uid(x)));if(S.view==="historico")a=a.filter(x=>S.hist[uid(x)]);if(!["inicio","filmes","series","favoritos","historico"].includes(S.view))a=a.filter(x=>norm(x.__category).includes(norm(S.view)));if(S.cat!=="Todos")a=a.filter(x=>x.__category===S.cat);return a}
function render(){S.filtered=filterItems();let groups=new Map;for(const i of S.filtered){let k=contentKind(i)==="serie"?norm(title(i))+"|"+norm(i.__category):uid(i);if(!groups.has(k))groups.set(k,i)}let a=[...groups.values()];$("status").textContent=`${a.length} títulos • ${S.source}`;$("catalog").innerHTML=a.slice(0,S.visible).map(i=>`<article class="poster" data-info="${safe(uid(i))}"><img src="${safe(poster(i))}" loading="lazy" decoding="async" onerror="this.onerror=null;this.style.opacity=0.2"><div><b>${safe(title(i))}</b><small>${safe(contentKind(i)==="serie"?(family(i).length+" episódios"):genre(i))}</small></div></article>`).join("");$("moreBtn").hidden=S.visible>=a.length}
function view(v){S.view=v;S.visible=36;document.querySelectorAll("[data-view]").forEach(b=>b.classList.toggle("active",b.dataset.view===v));$("sectionTitle").textContent=({inicio:"Em destaque",filmes:"Filmes",series:"Séries",favoritos:"Minha lista",historico:"Continuar assistindo"}[v]||readable(v));render();closeDrawer()}
function open(id){$(id).classList.add("open")}function close(id){$(id).classList.remove("open")}
function closeDrawer(){$("drawer").classList.remove("open");$("shade").classList.remove("open")}
function info(i){S.current=i;$("detailsPoster").src=poster(i);$("detailsBg").style.backgroundImage=`url("${backdrop(i)}")`;$("detailsTitle").textContent=title(i);$("detailsDesc").textContent=desc(i);$("detailsMeta").textContent=[year(i),genre(i),rating(i)].filter(Boolean).join(" • ");let fam=family(i).sort((a,b)=>season(a)-season(b)||epnum(a)-epnum(b)||title(a).localeCompare(title(b),"pt-BR"));$("episodesBox").hidden=contentKind(i)!=="serie";$("episodes").className="episodesCompact";$("episodes").innerHTML=fam.map((x,idx)=>{let sn=season(x)||1,en=epnum(x)||(idx+1);return `<button type="button" data-play="${safe(uid(x))}" data-path="${safe(x.__path)}" aria-label="Temporada ${sn}, episódio ${en}"><img loading="lazy" src="${safe(poster(x))}" onerror="this.onerror=null;this.style.opacity=0.2"><span><b>T${sn} • Ep. ${en}</b><small>${validUrl(url(x))?"Disponível":"Sem link"}</small></span><i>▶</i></button>`}).join("");$("favBtn").textContent=S.fav.has(uid(i))?"✓ Na minha lista":"＋ Minha lista";open("details")}
function findItem(id,path){return S.all.find(x=>uid(x)===id&&(path?x.__path===path:true))}
function saveHistory(i){S.hist[uid(i)]={time:Date.now(),title:title(i)};localStorage.setItem("rs_hist",JSON.stringify(S.hist))}
function message(t,bad=false){$("playerMsg").textContent=t;$("playerMsg").classList.toggle("bad",bad)}
function play(i){if(!i||!validUrl(url(i)))return alert("Este conteúdo está sem link válido.");S.current=i;saveHistory(i);close("details");open("player");$("playerTitle").textContent=title(i);$("playerSub").textContent=ep(i)||genre(i);let v=$("video"),f=$("frame"),u=url(i);v.pause();v.removeAttribute("src");f.removeAttribute("src");v.style.display="block";f.style.display="none";message("Carregando link original...");if(location.protocol==="https:"&&/^http:\/\//i.test(u)){message("O navegador pode bloquear vídeo HTTP dentro de página HTTPS. Use Abrir link se não tocar.",true)}v.src=u;v.load();v.play().catch(()=>message("Não iniciou automaticamente. Toque no botão Play ou use Abrir link.",true));v.onloadedmetadata=()=>message("Vídeo pronto");v.onerror=()=>message("O servidor recusou o player incorporado. Teste em Abrir link.",true)}
function search(q){q=norm(q);let a=[];for(const i of S.all){if(norm([title(i),genre(i),desc(i),ep(i)].join(" ")).includes(q)){a.push(i);if(a.length>=80)break}}$("searchResults").innerHTML=a.map(i=>`<button data-info="${safe(uid(i))}" data-path="${safe(i.__path)}"><img src="${safe(poster(i))}"><span>${safe(title(i))}<small>${safe(ep(i)||genre(i))}</small></span></button>`).join("")}
document.addEventListener("click",e=>{let v=e.target.closest("[data-view]")?.dataset.view;if(v)return view(v);let c=e.target.closest("[data-cat]")?.dataset.cat;if(c){S.cat=c;chips();render();return}let infoId=e.target.closest("[data-info]")?.dataset.info;if(infoId){let el=e.target.closest("[data-info]");let i=findItem(infoId,el.dataset.path);if(i)info(i);return}let playId=e.target.closest("[data-play]")?.dataset.play;if(playId){let el=e.target.closest("[data-play]");play(findItem(playId,el.dataset.path));return}let cl=e.target.closest("[data-close]")?.dataset.close;if(cl)close(cl)});
$("menuBtn").onclick=()=>{$("drawer").classList.add("open");$("shade").classList.add("open")};$("closeDrawer").onclick=$("shade").onclick=closeDrawer;$("heroPlay").onclick=()=>play(S.featured);$("heroInfo").onclick=()=>info(S.featured);$("detailsPlay").onclick=()=>play(S.current);$("closePlayer").onclick=()=>{let v=$("video");v.pause();close("player")};$("externalBtn").onclick=()=>S.current&&window.open(url(S.current),"_blank","noopener");$("altBtn").onclick=()=>{let u=url(S.current);$("video").style.display="none";$("frame").style.display="block";$("frame").src=u};$("fullBtn").onclick=()=>$("video").requestFullscreen?.();$("favBtn").onclick=()=>{let k=uid(S.current);S.fav.has(k)?S.fav.delete(k):S.fav.add(k);localStorage.setItem("rs_fav",JSON.stringify([...S.fav]));info(S.current)};$("searchBtn").onclick=$("bottomSearch").onclick=()=>{$("searchPanel").classList.add("open");$("searchInput").focus()};$("closeSearch").onclick=()=>$("searchPanel").classList.remove("open");$("searchInput").oninput=e=>search(e.target.value);$("clearSearch").onclick=()=>{$("searchInput").value="";search("")};$("moreBtn").onclick=()=>{S.visible+=36;render()};$("layoutBtn").onclick=()=>{$("catalog").classList.toggle("wide")};$("retryAccess").onclick=()=>load();
$("configBtn").onclick=()=>{alert("As fontes do aplicativo são controladas pelo Painel Total V11.")};
getControl().then(async c=>{S.control=c;await syncPendingUser();validateUserAndLoad()}).catch(()=>showLogin());

$("loginBtn").onclick=()=>loginOrRegister().catch(e=>showLogin("Erro: "+e.message));$("loginPhone").onkeydown=e=>{if(e.key==="Enter")$("loginBtn").click()};$("renewBtn").onclick=renewWhatsApp;$("changeAccountBtn").onclick=()=>{localStorage.removeItem(SESSION_KEY);window.REEL_CURRENT_USER=null;showLogin()};if($("accountBtn"))$("accountBtn").onclick=()=>{let u=window.REEL_CURRENT_USER;if(!u)return showLogin();if(confirm(`${u.name}\n${u.phone}\nPlano: ${daysLeft(u)} dia(s)\n\nSair da conta?`)){localStorage.removeItem(SESSION_KEY);showLogin()}};

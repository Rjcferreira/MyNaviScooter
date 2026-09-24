const state={connected:true,sport:true,mode:'Sport'};
const $=id=>document.getElementById(id);
function toast(message){const el=$('toast');el.textContent=message;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2200)}
function render(){
  $('connectionTitle').textContent=state.connected?'Conectado':'Desconectado';
  $('heartbeat').textContent=state.connected?'Verificação em 00:27':'Bluetooth desligado';
  $('speed').textContent=state.sport&&state.connected?'32':'22';
  $('sportTitle').textContent=state.sport&&state.connected?'Sport temporário':'Sport original';
  $('sportSubtitle').textContent=state.sport&&state.connected?'BLUETOOTH ATIVO · HEARTBEAT OK':'PERFIL ORIGINAL · MODO SEGURO';
  $('statusTitle').textContent=state.sport&&state.connected?'Tudo certo!':'Perfil original ativo';
  $('statusText').textContent=state.sport&&state.connected?'Sport temporário ativo com heartbeat Bluetooth.':'A scooter está protegida com as configurações originais.';
  $('status').classList.toggle('safe',!state.sport||!state.connected);
  if($('sportSwitch')) $('sportSwitch').checked=state.sport&&state.connected;
}
$('[data-view="dashboard"]');
document.querySelectorAll('.nav-item').forEach(btn=>btn.addEventListener('click',()=>{
  document.querySelectorAll('.nav-item').forEach(item=>item.classList.remove('active'));
  btn.classList.add('active');
  $('dashboardContent').style.display=btn.dataset.view==='dashboard'?'block':'none';
  document.querySelectorAll('.extra-view').forEach(view=>view.classList.remove('shown'));
  if(btn.dataset.view!=='dashboard') $('view-'+btn.dataset.view).classList.add('shown');
}));
$('sportBtn').addEventListener('click',()=>{if(!state.connected){toast('Liga o Bluetooth para ativar o Sport temporário');return}state.sport=!state.sport;render();toast(state.sport?'Sport temporário ativado':'Sport original restaurado')});
$('restoreBtn').addEventListener('click',()=>{state.sport=false;render();toast('Configurações originais restauradas')});
document.querySelectorAll('[data-mode]').forEach(btn=>btn.addEventListener('click',()=>{state.sport=false;state.mode=btn.dataset.mode;render();toast(`${state.mode} original selecionado`)}));
document.querySelector('.connection-card').addEventListener('click',()=>{state.connected=!state.connected;if(!state.connected)state.sport=false;render();toast(state.connected?'Bluetooth ligado':'Bluetooth desligado — modo seguro ativo')});
if($('backupBtn')) $('backupBtn').addEventListener('click',()=>{toast('Backup original criado e validado');$('backupTitle').textContent='Backup atualizado';$('backupText').textContent='Estado original guardado neste dispositivo.'});
if($('restoreFullBtn')) $('restoreFullBtn').addEventListener('click',()=>{state.sport=false;render();toast('Restauro completo simulado com sucesso')});
if($('startRoute')) $('startRoute').addEventListener('click',()=>toast('Navegação iniciada — rota segura selecionada'));
if($('sportSwitch')) $('sportSwitch').addEventListener('change',e=>{state.sport=e.target.checked&&state.connected;render();toast(state.sport?'Sport temporário ativado':'Sport original ativo')});
if($('languageBtn')) $('languageBtn').addEventListener('click',()=>toast('Seletor de idioma preparado: PT · EN · DE'));
render();

<template>
  <a class="skip-link" href="#loginMain">跳到登录表单</a>
  <section class="login-stage" id="loginStage" ref="loginStage" :style="cssVars" :class="interactionMode">
    <aside class="brand-panel" aria-hidden="true">
      <div class="brand-top">
        <div class="brand-mark">HR</div>
        <span>智能招聘系统</span>
      </div>
      <div class="brand-copy">
        <h1>欢迎回来</h1>
        <p>进入招聘看板、需求管理、人才库与面试计划。系统将根据演示角色显示对应菜单权限。</p>
        <div class="radar-scene">
          <div class="pipeline"></div>
          <div class="node one"></div><div class="node two"></div>
          <div class="node three"></div><div class="node four"></div>
          <div class="focus-beam"></div>
          <div class="talent-radar">
            <div class="radar-console"></div>
            <div class="lens-deck">
              <div class="sensor-row">
                <div class="sensor"><div class="pupil"></div></div>
                <div class="sensor"><div class="pupil"></div></div>
              </div>
              <div class="status-line"></div>
            </div>
          </div>
          <div class="signal-card left"><span></span><span></span><span></span></div>
          <div class="signal-card right"><span></span><span></span><span></span></div>
        </div>
      </div>
      <div class="brand-footer">
        <span>招聘管理</span><span>人才库</span><span>面试计划</span>
      </div>
    </aside>

    <main class="login-side" id="loginMain">
      <div class="login-box">
        <div class="mobile-brand"><div class="brand-mark">HR</div><span>智能招聘系统</span></div>
        <h2>登录</h2>
        <div class="sub">选择演示角色后进入系统</div>

        <span class="role-label">演示角色</span>
        <div class="roles" ref="roleContainer">
          <button v-for="role in roles" :key="role.key"
            type="button"
            class="role-card"
            :class="{ sel: selectedRole === role.key }"
            :data-role="role.key"
            @click="selRole(role.key)"
          >
            <span class="icon">{{ role.icon }}</span>
            <span class="role-name">{{ role.name }}</span>
            <span class="role-note">{{ role.note }}</span>
          </button>
        </div>

        <div class="form-group"><label for="username">账号</label><input type="text" id="username" v-model="username" placeholder="输入账号" autocomplete="username"></div>
        <div class="form-group"><label for="password">密码</label><input type="password" id="password" v-model="password" placeholder="输入密码" autocomplete="current-password"></div>

        <div class="login-options">
          <label><input type="checkbox" id="remember"> 30 天内记住我</label>
          <a href="#" @click.prevent>忘记密码？</a>
        </div>

        <button class="btn-login" @click="login">登录</button>
        <div class="login-foot">原型演示，点击登录即可进入</div>
      </div>
    </main>
  </section>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const loginStage = ref(null);
const selectedRole = ref('admin');
const username = ref('admin');
const password = ref('123456');
const interactionMode = ref('');

const cssVars = reactive({
  '--mx': '50%', '--my': '50%',
  '--pupil-x': '0px', '--pupil-y': '0px',
  '--tilt-x': '0deg', '--tilt-y': '0deg',
  '--beam-x': '0px'
});

const roles = [
  { key: 'admin', icon: 'A', name: '管理员', note: '全部功能与系统配置' },
  { key: 'hr', icon: 'HR', name: 'HR 专员', note: '招聘全流程操作' },
  { key: 'dept_head', icon: 'D', name: '部门负责人', note: '本部门需求审批' },
  { key: 'employee', icon: 'E', name: '基层员工', note: '提交与查看本人需求' },
  { key: 'interviewer', icon: 'I', name: '面试官', note: '面试评价与查看' },
  { key: 'temp_interviewer', icon: 'T', name: '临时面试官', note: '仅本次分配场次' },
  { key: 'no_recruit', icon: 'N', name: '无招聘权限', note: '侧边栏隐藏' }
];

function selRole(key) {
  selectedRole.value = key;
}

function login() {
  sessionStorage.removeItem('hr_temp_interviewer');
  if (selectedRole.value === 'temp_interviewer') {
    localStorage.setItem('hr_role', 'interviewer');
    sessionStorage.setItem('hr_temp_interviewer', 'true');
  } else {
    localStorage.setItem('hr_role', selectedRole.value);
  }
  localStorage.setItem('hr_user', username.value || '用户');
  router.push('/recruit-dashboard');
}

// Mouse tracking for radar animation
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

let userInputEl = null;
let passInputEl = null;

function handleUsernameFocus() { interactionMode.value = 'username-mode'; }
function handleUsernameBlur() { interactionMode.value = ''; }
function handlePasswordFocus() { interactionMode.value = 'password-mode'; }
function handlePasswordBlur() { interactionMode.value = ''; }

function track(event) {
  if (!loginStage.value) return;
  const rect = loginStage.value.getBoundingClientRect();
  const x = ((event.clientX - rect.left) / rect.width) * 100;
  const y = ((event.clientY - rect.top) / rect.height) * 100;
  const pupilX = Math.max(-11, Math.min(11, (x - 50) / 5.4));
  const pupilY = Math.max(-7, Math.min(7, (y - 42) / 7.8));

  cssVars['--mx'] = x.toFixed(2) + '%';
  cssVars['--my'] = y.toFixed(2) + '%';
  cssVars['--pupil-x'] = pupilX.toFixed(2) + 'px';
  cssVars['--pupil-y'] = pupilY.toFixed(2) + 'px';
  cssVars['--tilt-y'] = ((x - 50) / 46).toFixed(2) + 'deg';
  cssVars['--tilt-x'] = ((50 - y) / 60).toFixed(2) + 'deg';
}

function resetVars() {
  cssVars['--mx'] = '50%'; cssVars['--my'] = '50%';
  cssVars['--pupil-x'] = '0px'; cssVars['--pupil-y'] = '0px';
  cssVars['--tilt-x'] = '0deg'; cssVars['--tilt-y'] = '0deg';
}

onMounted(() => {
  userInputEl = document.getElementById('username');
  passInputEl = document.getElementById('password');
  if (userInputEl) {
    userInputEl.addEventListener('focus', handleUsernameFocus);
    userInputEl.addEventListener('blur', handleUsernameBlur);
  }
  if (passInputEl) {
    passInputEl.addEventListener('focus', handlePasswordFocus);
    passInputEl.addEventListener('blur', handlePasswordBlur);
  }
  if (!reduceMotion && loginStage.value) {
    loginStage.value.addEventListener('mousemove', track);
    loginStage.value.addEventListener('mouseleave', resetVars);
  }
});

onUnmounted(() => {
  if (userInputEl) {
    userInputEl.removeEventListener('focus', handleUsernameFocus);
    userInputEl.removeEventListener('blur', handleUsernameBlur);
  }
  if (passInputEl) {
    passInputEl.removeEventListener('focus', handlePasswordFocus);
    passInputEl.removeEventListener('blur', handlePasswordBlur);
  }
  if (loginStage.value) {
    loginStage.value.removeEventListener('mousemove', track);
    loginStage.value.removeEventListener('mouseleave', resetVars);
  }
});
</script>

<style>
.login-stage{
  width:100%; min-height:100vh;
  display:grid; grid-template-columns:minmax(560px,1.08fr) minmax(440px,.74fr);
  overflow:hidden; background:#F6F8FB; color:#172033;
  font-family:Inter,"PingFang SC","Microsoft YaHei",Arial,sans-serif;
}
.skip-link{position:fixed;left:16px;top:16px;z-index:30;transform:translateY(-80px);border-radius:8px;background:#172033;color:#fff;padding:10px 14px;text-decoration:none;font-size:13px;font-weight:800}
.skip-link:focus{transform:translateY(0)}
.brand-panel{position:relative;min-height:100vh;overflow:hidden;display:flex;flex-direction:column;justify-content:space-between;padding:48px clamp(34px,5vw,76px);background:#101827;color:#FFFFFF}
.brand-panel::before{content:"";position:absolute;inset:0;opacity:.72;background-size:36px 36px}
.brand-panel::after{content:"";position:absolute;width:520px;height:520px;right:-230px;bottom:-220px;border-radius:0;background:rgba(79,110,247,.14);filter:blur(10px);transform:rotate(18deg)}
.brand-top,.brand-copy,.brand-footer,.radar-scene{position:relative;z-index:1}
.brand-top{display:flex;align-items:center;gap:12px;font-weight:850;font-size:16px}
.brand-mark{width:38px;height:38px;border-radius:8px;display:grid;place-items:center;background:#FFFFFF;color:#172033;font-weight:900;box-shadow:none}
.brand-copy{max-width:650px}
.brand-copy h1{margin:0;max-width:620px;font-size:clamp(44px,4.8vw,68px);line-height:1;letter-spacing:0;font-weight:900}
.brand-copy p{margin:22px 0 0;max-width:560px;color:rgba(244,247,251,.8);font-size:15px;line-height:1.9}
.radar-scene{height:392px;width:min(660px,100%);margin:34px auto 0;transform:perspective(920px) rotateX(var(--tilt-x)) rotateY(var(--tilt-y));transition:transform .12s ease-out}
.pipeline{position:absolute;inset:56px 18px 36px;border:1px solid rgba(255,255,255,.18);border-radius:8px;background:rgba(255,255,255,.06);box-shadow:inset 0 1px 0 rgba(255,255,255,.14)}
.pipeline::before,.pipeline::after{content:"";position:absolute;left:42px;right:42px;height:1px;background:rgba(255,255,255,.28)}
.pipeline::before{top:92px}.pipeline::after{bottom:96px}
.node{position:absolute;width:48px;height:48px;border:1px solid rgba(255,255,255,.36);border-radius:8px;background:rgba(255,255,255,.1);box-shadow:0 16px 38px rgba(23,32,51,.18)}
.node::after{content:"";position:absolute;inset:15px;border-radius:4px;background:#FFFFFF;opacity:.8}
.node.one{left:58px;top:70px}.node.two{right:92px;top:58px}
.node.three{left:150px;bottom:76px}.node.four{right:44px;bottom:82px}
.focus-beam{position:absolute;left:50%;top:130px;width:220px;height:220px;border-radius:50%;transform:translateX(calc(-50% + var(--beam-x)));background:rgba(79,110,247,.18);filter:blur(2px);transition:transform .18s ease-out}
.talent-radar{position:absolute;left:50%;bottom:54px;width:350px;height:238px;transform:translateX(-50%)}
.radar-console{position:absolute;left:0;right:0;bottom:0;height:122px;border:1px solid rgba(255,255,255,.36);border-radius:8px;background:#F8FAFC;box-shadow:0 26px 70px rgba(2,6,23,.24)}
.radar-console::before{content:"";position:absolute;left:26px;top:26px;width:118px;height:8px;border-radius:999px;background:#C7D2E3;box-shadow:0 26px 0 #D9E1EE, 154px 0 0 #C7D2E3, 154px 26px 0 #D9E1EE}
.radar-console::after{content:"";position:absolute;left:26px;right:26px;bottom:22px;height:1px;background:#AEB9CA}
.lens-deck{position:absolute;left:50%;top:6px;width:238px;height:130px;border:1px solid rgba(255,255,255,.22);border-radius:12px;background:#172033;transform:translateX(-50%);box-shadow:0 26px 64px rgba(23,32,51,.32)}
.lens-deck::before,.lens-deck::after{content:"";position:absolute;top:18px;bottom:18px;width:1px;background:rgba(255,255,255,.12)}
.lens-deck::before{left:72px}.lens-deck::after{right:72px}
.sensor-row{position:absolute;left:44px;top:28px;display:flex;gap:34px}
.sensor{width:54px;height:54px;border:8px solid rgba(79,110,247,.28);border-radius:50%;background:#EAF0FA;display:grid;place-items:center;overflow:hidden;box-shadow:0 0 0 1px rgba(79,110,247,.16), inset 0 0 0 1px rgba(79,110,247,.18);transition:height .18s ease,transform .18s ease,border-radius .18s ease}
.pupil{width:18px;height:18px;border-radius:50%;background:#4F6EF7;transform:translate(var(--pupil-x),var(--pupil-y));transition:transform .08s ease-out,opacity .12s ease}
.status-line{position:absolute;left:50%;bottom:22px;width:96px;height:6px;border-radius:999px;background:#4F6EF7;transform:translateX(-50%);transition:width .18s ease,transform .18s ease}
.signal-card{position:absolute;border:1px solid rgba(255,255,255,.18);border-radius:8px;background:rgba(255,255,255,.06);backdrop-filter:blur(8px);box-shadow:0 18px 48px rgba(23,32,51,.16)}
.signal-card.left{left:20px;top:176px;width:154px;height:84px}
.signal-card.right{right:18px;top:116px;width:136px;height:112px}
.signal-card span{display:block;height:8px;margin:16px 18px 0;border-radius:999px;background:rgba(255,255,255,.68)}
.signal-card span:nth-child(2){width:56%}.signal-card span:nth-child(3){width:74%}
.login-stage.username-mode .focus-beam{--beam-x:28px}
.login-stage.username-mode .talent-radar{transform:translateX(-48%)}
.login-stage.username-mode .lens-deck{transform:translateX(-50%) rotate(2deg)}
.login-stage.password-mode .focus-beam{--beam-x:-18px}
.login-stage.password-mode .sensor{height:10px;border-width:6px;border-radius:999px;transform:translateY(20px)}
.login-stage.password-mode .pupil{opacity:0}
.login-stage.password-mode .status-line{width:68px;transform:translateX(-50%)}
.brand-footer{display:flex;gap:10px;flex-wrap:wrap;color:rgba(255,255,255,.72);font-size:12px}
.brand-footer span{padding:6px 10px;border:1px solid rgba(255,255,255,.16);border-radius:999px;color:rgba(244,247,251,.82);font-size:12px;background:rgba(255,255,255,.06)}
.login-side{min-height:100vh;display:grid;place-items:center;padding:42px clamp(24px,5vw,72px);background:#FFFFFF;border-left:1px solid #E1E6EF}
.login-box{width:100%;max-width:448px;border:1px solid #E1E6EF;border-radius:12px;padding:28px;background:#FFFFFF;box-shadow:0 24px 60px rgba(15,23,42,.08)}
.mobile-brand{display:none;align-items:center;justify-content:center;gap:10px;margin-bottom:34px;font-weight:850}
.login-box h2{margin:0;font-size:28px;line-height:1.2;letter-spacing:0;font-weight:900;color:#172033}
.login-box .sub{margin:8px 0 24px;color:#5B6475;font-size:14px}
.role-label{display:block;margin-bottom:10px;color:#172033;font-size:13px;font-weight:750}
.roles{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:22px}
.role-card{min-height:74px;border:1px solid #E1E6EF;border-radius:8px;background:#FFFFFF;color:#172033;cursor:pointer;display:flex;flex-direction:column;justify-content:center;gap:5px;padding:10px 8px;transition:transform .16s ease,border-color .16s ease,background .16s ease,box-shadow .16s ease;font-family:inherit}
.role-card:hover{transform:translateY(-1px);border-color:#4F6EF7;box-shadow:0 0 0 3px rgba(79,110,247,.08)}
.role-card:focus-visible,.btn-login:focus-visible,.login-options a:focus-visible,.form-group input:focus-visible{outline:3px solid rgba(79,110,247,.35);outline-offset:2px}
.role-card.sel{border-color:#4F6EF7;background:#F7FAFF;box-shadow:inset 3px 0 0 #4F6EF7}
.role-card .icon{font-size:20px;font-weight:900;border-radius:6px;background:#F1F5F9;color:#344054}
.role-card.sel .icon{background:#4F6EF7;color:#FFFFFF}
.role-card .role-name{font-size:13px;font-weight:850}
.role-card .role-note{color:#6B7280;font-size:11px;line-height:1.35}
.form-group{margin-bottom:17px}
.form-group label{display:block;margin-bottom:7px;color:#172033;font-size:13px;font-weight:750}
.form-group input{width:100%;height:42px;border:1px solid #E1E6EF;border-radius:8px;background:#FFFFFF;color:#172033;padding:0 16px;font-size:14px;outline:none;transition:border-color .16s ease,box-shadow .16s ease;box-sizing:border-box}
.form-group input::placeholder{color:#A7B0C1}
.form-group input:focus{border-color:#4F6EF7;box-shadow:0 0 0 3px rgba(79,110,247,.12)}
.login-options{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:2px 0 22px;color:#5B6475;font-size:13px}
.login-options label{display:flex;align-items:center;gap:8px}
.login-options input{width:15px;height:15px;accent-color:#4F6EF7}
.login-options a{color:#4F6EF7;text-decoration:none;font-weight:750}
.login-options a:hover{text-decoration:underline}
.btn-login{width:100%;height:46px;border:0;border-radius:8px;color:#FFFFFF;background:#4F6EF7;font-size:14px;font-weight:900;cursor:pointer;box-shadow:none;transition:transform .16s ease,background .16s ease;font-family:inherit}
.btn-login:hover{transform:translateY(-1px);background:#3D54D4;box-shadow:0 18px 34px rgba(79,110,247,.28)}
.login-foot{margin-top:16px;text-align:center;color:#8C95A6;font-size:12px}
@media(max-width:980px){
  .login-stage{grid-template-columns:1fr}
  .brand-panel{display:none}
  .login-side{padding:32px 20px}
  .mobile-brand{display:flex}
}
@media(max-width:520px){
  .roles{grid-template-columns:1fr}
  .role-card{min-height:64px}
  .login-options{align-items:flex-start;flex-direction:column}
}
@media(prefers-reduced-motion:reduce){
  .brand-panel::before,.radar-scene,.talent-radar,.lens-deck,.focus-beam,.sensor,.pupil,.role-card,.btn-login{transition:none!important;transform:none!important}
}
</style>
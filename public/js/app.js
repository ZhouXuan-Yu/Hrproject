// 智能招聘系统 · 菜单路由 v3
// 6 角色权限矩阵 + 候选⼈抽屉组件 + 动态菜单显隐

// ========== 菜单定义 ==========
var MENU_ROUTES = [
  { id:'recruit-dashboard', label:'招聘看板', href:'/recruit-dashboard' },
  { id:'recruit-demand',    label:'需求管理',   href:'/recruit-demand' },
  { id:'recruit-talent',    label:'人才库',     href:'/recruit-talent' },
  { id:'recruit-interview', label:'面试计划',   href:'/recruit-interview' },
  { id:'recruit-ai',        label:'招聘辅助中心', href:'/recruit-ai' },
  { id:'recruit-config',    label:'招聘基础配置',      href:'/recruit-config' },
];

// ========== 6 角色权限矩阵 ==========
// 角色 key → 可见菜单 ID 列表
var ROLE_MENUS = {
  'no_recruit':      [],                                                      // 无招聘业务员工：侧边栏完全隐藏
  'employee':        ['recruit-dashboard','recruit-demand'],                 // 普通员工：只看自己的需求
  'dept_head':       ['recruit-dashboard','recruit-demand'],                 // 部门负责人：本部门数据
  'interviewer':     ['recruit-dashboard','recruit-interview'],              // 常规面试官：看板+面试计划（仅我的面试）
  'temp_interviewer':['recruit-dashboard','recruit-interview'],              // 临时面试官：看板+面试计划（评价完回收菜单）
  'hr':              ['recruit-dashboard','recruit-demand','recruit-talent','recruit-interview'], // HR 专员
  'admin':           ['recruit-dashboard','recruit-demand','recruit-talent','recruit-interview','recruit-ai','recruit-config'], // 管理员：全量可见
};

// ========== 评分系统：衰减系数 & 综合推荐分 ==========
var DECAY_COEFFICIENT = [
  {maxDays: 30,  coeff: 1.0},
  {maxDays: 90,  coeff: 0.85},
  {maxDays: 999, coeff: 0.70}
];
function getDecayCoefficient(daysAgo){
  for(var i=0; i<DECAY_COEFFICIENT.length; i++){
    if(daysAgo <= DECAY_COEFFICIENT[i].maxDays) return DECAY_COEFFICIENT[i].coeff;
  }
  return 0.70;
}
// 存量简历综合推荐分 = 画像分×10% + (原始匹配分×衰减系数)×90%
function calcRecommendScore(profileScore, matchScore, calcDaysAgo){
  var decay = getDecayCoefficient(calcDaysAgo);
  return Math.round((profileScore * 0.1 + (matchScore * decay) * 0.9) * 10) / 10;
}
// 直接投递综合分 = 画像分×10% + 原始匹配分×90%（无衰减）
function calcDirectScore(profileScore, matchScore){
  return Math.round((profileScore * 0.1 + matchScore * 0.9) * 10) / 10;
}
function profileColor(score){
  return score >= 80 ? 'var(--c-done)' : (score >= 60 ? 'var(--c-warn)' : 'var(--c-draft)');
}
function profileGradeLabel(data){
  return (data.profileGrade||'B') + ' · ' + (data.profileScore||0);
}
function matchStaleDays(daysAgo){
  return daysAgo > 30 ? '<span class="match-stale-warn">⚡ 匹配分已超期，建议重新匹配</span>' : '';
}
function matchStaleHint(daysAgo){
  return daysAgo > 30 ? '<span class="match-stale-hint">匹配记录生成于 '+daysAgo+' 天前</span>' : '';
}

var ROLE_LABELS = {
  'no_recruit':'无权限员工','employee':'基层员工','dept_head':'部门负责人',
  'interviewer':'面试官','temp_interviewer':'临时面试官','hr':'HR 专员','admin':'管理员'
};
var ROLE_CLASS = {
  'admin':'role-admin','hr':'role-hr','interviewer':'role-interviewer',
  'temp_interviewer':'role-interviewer','employee':'role-hr','dept_head':'role-admin'
};
var HR_DEPARTMENTS = ['技术部','产品部','运营部','数据部','财务部','人力资源部'];
function renderDepartmentOptions(selected, includeAll){
  var opts = includeAll ? '<option value="all">全公司</option>' : '';
  HR_DEPARTMENTS.forEach(function(d){
    opts += '<option value="'+d+'"'+(d===selected?' selected':'')+'>'+d+'</option>';
  });
  return opts;
}

// ========== 角色 & 用户 ==========
function getRole(){
  // 优先检查临时面试官身份（session 级别，登出清除）
  if(sessionStorage.getItem('hr_temp_interviewer')==='true') return 'temp_interviewer';
  return localStorage.getItem('hr_role')||'hr';
}
function getUser(){ return localStorage.getItem('hr_user')||'用户'; }
function getVisibleMenus(role){
  var ids = ROLE_MENUS[role] || ROLE_MENUS['employee'];
  return MENU_ROUTES.filter(function(r){ return ids.indexOf(r.id)!==-1; });
}

// ========== 侧边栏渲染 ==========
function renderSidebar(activeId){
  var role = getRole();
  var visible = getVisibleMenus(role);
  var h = '';

  // 无招聘权限：不渲染招聘管理一级菜单
  if(visible.length===0){
    h += '<div class="logo"><div class="logo-icon">HR</div><div class="logo-text">智能招聘系统<span>Recruitment v0.1</span></div></div>';
    h += '<nav><div style="padding:40px 18px;text-align:center;font-size:12px;color:#5a6180">暂无招聘模块权限<br><br>请联系管理员开通</div></nav>';
    h += '<div class="user-info"><div class="avatar">'+getUser().charAt(0).toUpperCase()+'</div><div>'+getUser()+'<span class="role-badge" style="background:#fff;border:1px solid var(--c-draft);color:var(--c-draft)">无权限</span></div><a class="logout" href="/login" onclick="localStorage.clear();sessionStorage.clear()">退出</a></div>';
  } else {
    h += '<div class="logo"><div class="logo-icon">HR</div><div class="logo-text">智能招聘系统<span>Recruitment v0.1</span></div></div>';
    h += '<nav>';
    h += '<div class="nav-main-menu open" id="navMainMenu">';
    h += '<div class="nav-main-label" onclick="document.getElementById(\'navMainMenu\').classList.toggle(\'open\')">招聘管理 <span class="nav-arrow">▾</span></div>';
    h += '<div class="nav-flyout">';
    visible.forEach(function(r){
      h += '<a href="'+r.href+'" class="nav-flyout-item'+(r.id===activeId?' active':'')+'"><span class="nav-dot"></span>'+r.label+'</a>';
    });
    h += '</div></div>';
    h += '</nav>';
    var badgeClass = ROLE_CLASS[role]||'';
    var badgeLabel = ROLE_LABELS[role]||role;
    h += '<div class="user-info"><div class="avatar">'+getUser().charAt(0).toUpperCase()+'</div><div>'+getUser()+'<span class="role-badge '+badgeClass+'">'+badgeLabel+'</span></div><a class="logout" href="/login" onclick="localStorage.clear();sessionStorage.clear()">退出</a></div>';
  }

  document.getElementById('sidebar').innerHTML = h;

  // 面试官自动切「我的面试」Tab
  if((role==='interviewer'||role==='temp_interviewer') && document.getElementById('interviewTabs')){
    setTimeout(function(){
      var tabs = document.querySelectorAll('#interviewTabs .tab');
      var panels = document.querySelectorAll('.tab-panel');
      tabs.forEach(function(t){ t.classList.remove('active'); });
      panels.forEach(function(p){ p.classList.remove('active'); });
      var mineTab = document.querySelector('#interviewTabs .tab[data-tab="mine"]');
      var minePanel = document.getElementById('panel-mine');
      if(mineTab) mineTab.classList.add('active');
      if(minePanel) minePanel.classList.add('active');
    },50);
  }
}

// ========== 候选⼈详情抽屉（通⽤组件） ==========
function openCandidateDrawer(data){
  // data: {name, phone, email, education, years, company, grade, strengths:[], weaknesses:[], salary, tags_hit:[], tags_miss:[], matchRecords:[], interviewRecords:[], timeline:[], resumeUrl}
  var overlay = document.getElementById('drawerOverlay');
  var drawer = document.getElementById('candidateDrawer');

  if(!overlay){
    overlay = document.createElement('div'); overlay.id='drawerOverlay'; overlay.className='drawer-overlay';
    overlay.onclick = closeCandidateDrawer;
    document.body.appendChild(overlay);
    drawer = document.createElement('div'); drawer.id='candidateDrawer'; drawer.className='drawer';
    document.body.appendChild(drawer);
  }

  var tagsHitHtml = (data.tags_hit||[]).map(function(t){ return '<span class="tag-item tag-hit">'+t.label+'<span class="tag-score">'+t.score+'</span></span>'; }).join('');
  var tagsMissHtml = (data.tags_miss||[]).map(function(t){ return '<span class="tag-item tag-miss">'+t.label+'</span>'; }).join('');

  var matchHtml = (data.matchRecords||[]).map(function(m){ return '<tr><td>'+m.position+'</td><td style="font-weight:700;color:var(--c-primary)">'+m.score+'</td><td>'+m.time+'</td><td>'+m.status+'</td></tr>'; }).join('');
  var interviewHtml = (data.interviewRecords||[]).map(function(i){ return '<tr><td>'+i.position+'</td><td>'+i.round+'</td><td>'+i.interviewer+'</td><td>'+i.time+'</td><td><span class="st st-'+i.resultClass+'">'+i.result+'</span></td></tr>'; }).join('');
  var timelineHtml = (data.timeline||[]).map(function(t){ return '<div class="tl-item '+t.cls+'"><div class="tl-date">'+t.date+'</div><div class="tl-desc">'+t.desc+'</div></div>'; }).join('');

  var h = '';
  h += '<div class="drawer-header"><h3>👤 '+data.name+'</h3><button class="drawer-close" onclick="closeCandidateDrawer()">✕</button></div>';
  h += '<div class="drawer-body">';

  // 1. 基本信息
  h += '<div class="drawer-section"><div class="drawer-section-title">基本信息</div>';
  h += '<div class="info-row"><span class="k">手机</span><span class="v">'+data.phone+'</span></div>';
  h += '<div class="info-row"><span class="k">邮箱</span><span class="v">'+data.email+'</span></div>';
  h += '<div class="info-row"><span class="k">学历</span><span class="v">'+data.education+'</span></div>';
  h += '<div class="info-row"><span class="k">工作年限</span><span class="v">'+data.years+'</span></div>';
  h += '<div class="info-row"><span class="k">最近公司</span><span class="v">'+data.company+'</span></div>';
  h += '</div>';

  // 2. Candidate profile card
  h += '<div class="drawer-section"><div class="drawer-section-title">候选人画像</div>';
  h += '<div class="portrait-card">';
  h += '<div class="portrait-header"><div><div class="portrait-grade">'+data.grade+'</div><div class="portrait-grade-label">综合评级</div></div><div style="text-align:right"><div style="font-size:12px;color:var(--c-sub)">薪资预估</div><div class="portrait-salary">'+data.salary+'</div></div></div>';
  h += '<div class="portrait-strengths"><span class="label">✦ 核心优势</span><ul>'+(data.strengths||[]).map(function(s){return '<li>'+s+'</li>';}).join('')+'</ul></div>';
  h += '<div class="portrait-weaknesses"><span class="label">✧ 待关注</span><ul>'+(data.weaknesses||[]).map(function(w){return '<li>'+w+'</li>';}).join('')+'</ul></div>';
  h += '</div></div>';

  // 3. 结构化简历
  h += '<div class="drawer-section"><div class="drawer-section-title">结构化简历</div>';
  h += '<div class="accordion open"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">🎓 教育经历 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.eduHistory||'暂无数据')+'</div></div>';
  h += '<div class="accordion"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">💼 工作经历 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.workHistory||'暂无数据')+'</div></div>';
  h += '<div class="accordion"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">📁 项目经历 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.projectHistory||'暂无数据')+'</div></div>';
  h += '</div>';

  // 4. 技能标签
  h += '<div class="drawer-section"><div class="drawer-section-title">技能标签</div>';
  h += '<div style="font-size:11px;color:var(--c-done);margin-bottom:6px">✅ 命中标签</div><div class="tag-cloud" style="margin-bottom:10px">'+(tagsHitHtml||'<span style="font-size:12px;color:var(--c-sub)">暂无</span>')+'</div>';
  h += '<div style="font-size:11px;color:var(--c-reject);margin-bottom:6px">❌ 缺失标签</div><div class="tag-cloud">'+(tagsMissHtml||'<span style="font-size:12px;color:var(--c-sub)">暂无</span>')+'</div>';
  h += '</div>';

  // 5. 岗位匹配记录
  h += '<div class="drawer-section"><div class="drawer-section-title">岗位匹配记录</div>';
  h += '<table style="font-size:12px"><thead><tr><th>岗位</th><th>匹配分</th><th>时间</th><th>状态</th></tr></thead><tbody>'+(matchHtml||'<tr><td colspan="4" style="color:var(--c-sub);text-align:center">暂无匹配记录</td></tr>')+'</tbody></table>';
  h += '</div>';

  // 6. 面试记录
  h += '<div class="drawer-section"><div class="drawer-section-title">面试记录</div>';
  h += '<table style="font-size:12px"><thead><tr><th>岗位</th><th>轮次</th><th>面试官</th><th>时间</th><th>结果</th></tr></thead><tbody>'+(interviewHtml||'<tr><td colspan="5" style="color:var(--c-sub);text-align:center">暂无面试记录</td></tr>')+'</tbody></table>';
  h += '</div>';

  // 7. 状态时间线
  h += '<div class="drawer-section"><div class="drawer-section-title">状态时间线</div>';
  h += '<div class="timeline">'+timelineHtml+'</div>';
  h += '</div>';

  // 8. 原始简历
  h += '<div class="drawer-section"><div class="drawer-section-title">原始简历</div>';
  h += '<a href="'+data.resumeUrl+'" target="_blank" class="btn btn-outline btn-sm">📄 查看原始 PDF</a>';
  h += '</div>';

  h += '</div>'; // drawer-body
  drawer.innerHTML = h;

  overlay.classList.add('open');
  drawer.classList.add('open');
  document.body.style.overflow = 'hidden';
}

function closeCandidateDrawer(){
  var overlay = document.getElementById('drawerOverlay');
  var drawer = document.getElementById('candidateDrawer');
  if(overlay) overlay.classList.remove('open');
  if(drawer) drawer.classList.remove('open');
  document.body.style.overflow = '';
}

// ========== 全局面试安排弹窗（人才库/面试计划共用） ==========
var MOCK_INTERVIEWERS = [
  {id:'EMP010',name:'李面试官',dept:'技术部',role:'面试官'},
  {id:'EMP011',name:'王面试官',dept:'技术部',role:'面试官'},
  {id:'EMP020',name:'刘博',dept:'技术部',role:'业务负责人'},
  {id:'EMP030',name:'周博',dept:'产品部',role:'业务负责人'},
  {id:'EMP040',name:'陈总',dept:'运营部',role:'业务负责人'},
  {id:'EMP100',name:'张HR',dept:'人力资源部',role:'HR'},
];
var globalRoundCounter = 0;

function openGlobalScheduleModal(candidate, position, dept){
  // 移除已有弹窗
  var old = document.getElementById('globalScheduleModal');
  if(old) old.remove();

  globalRoundCounter = 0;
  var overlay = document.createElement('div');
  overlay.id = 'globalScheduleModal';
  overlay.className = 'modal-overlay';
  overlay.style.display = 'flex';
  overlay.onclick = function(e){ if(e.target===overlay) closeGlobalScheduleModal(); };

  var html = '<div class="modal-box" style="width:620px;max-height:90vh;overflow-y:auto">';
  html += '<h3>📅 安排面试</h3>';
  html += '<div class="form-row" style="margin-bottom:10px">';
  html += '<div class="form-group"><label>目标岗位 <span style="font-size:10px;color:var(--c-sub);font-weight:400">自动回填</span></label><input type="text" id="gsPosition" value="'+(position||'')+'" readonly style="background:#f8fafc"></div>';
  html += '<div class="form-group"><label>所属部门 <span style="font-size:10px;color:var(--c-sub);font-weight:400">自动回填</span></label><input type="text" id="gsDept" value="'+(dept||'')+'" readonly style="background:#f8fafc"></div>';
  html += '</div>';
  html += '<div class="form-group" style="margin-bottom:10px"><label>候选人</label><input type="text" id="gsCandidate" value="'+(candidate||'')+'" placeholder="输入姓名搜索"></div>';

  // 联系确认
  html += '<div style="margin-bottom:12px;padding:12px 14px;background:#FFFBF5;border-radius:8px;border:1px solid #FEE9CC">';
  html += '<div style="font-size:12px;font-weight:600;margin-bottom:6px;color:var(--c-text)">📞 候选人联系确认</div>';
  html += '<div style="display:flex;gap:14px;font-size:13px;flex-wrap:wrap">';
  html += '<label style="cursor:pointer"><input type="radio" name="gsContact" value="none" checked onchange="toggleGsContact()"> 未联系</label>';
  html += '<label style="cursor:pointer"><input type="radio" name="gsContact" value="phone" onchange="toggleGsContact()"> 📱 已电话联系</label>';
  html += '<label style="cursor:pointer"><input type="radio" name="gsContact" value="mail" onchange="toggleGsContact()"> 📧 已邮件确认</label>';
  html += '<label style="cursor:pointer"><input type="radio" name="gsContact" value="feishu" onchange="toggleGsContact()"> 💬 已飞书确认</label>';
  html += '<label style="cursor:pointer"><input type="radio" name="gsContact" value="manager" onchange="toggleGsContact()"> 👔 内部已沟通上级</label>';
  html += '</div>';
  html += '<div id="gsContactWarn" style="margin-top:8px;font-size:12px;color:var(--c-warn);font-weight:600">⚠️ 尚未确认候选人意向，请先通过电话、邮件或飞书完成联系；内部员工需先沟通直属上级。</div>';
  html += '</div>';

  // 轮次配置
  html += '<div style="margin-bottom:12px" id="gsRoundSection">';
  html += '<label style="font-size:12px;font-weight:600;display:block;margin-bottom:8px">面试轮次配置 <span style="font-size:10px;color:var(--c-sub);font-weight:400">每轮独立面试官</span></label>';
  html += '<div id="gsRoundContainer" style="display:flex;flex-direction:column;gap:10px"></div>';
  html += '<button class="btn btn-outline btn-sm" style="margin-top:8px" onclick="addGlobalRound()">+ 添加轮次</button>';
  html += '</div>';

  html += '<div class="modal-actions" style="margin-top:8px">';
  html += '<button class="btn btn-ghost btn-sm" onclick="closeGlobalScheduleModal()">取消</button>';
  html += '<button class="btn btn-primary btn-sm" onclick="submitGlobalSchedule()">确认安排 · 自动创建飞书会议</button>';
  html += '</div>';
  html += '</div>';

  overlay.innerHTML = html;
  document.body.appendChild(overlay);

  // 预设轮次
  var preset = {高级Java工程师:['初试','复试','终面'],前端工程师:['初试','复试'],数据分析师:['初试'],产品经理:['初试','复试']};
  var rounds = preset[position]||['初试'];
  rounds.forEach(function(r){ addGlobalRound(r, dept); });
}

function closeGlobalScheduleModal(){
  var el = document.getElementById('globalScheduleModal');
  if(el) el.remove();
}

function toggleGsContact(){
  var v = document.querySelector('input[name="gsContact"]:checked').value;
  var warn = document.getElementById('gsContactWarn');
  var section = document.getElementById('gsRoundSection');
  if(warn) warn.style.display = v==='none' ? 'block' : 'none';
  if(section) section.style.opacity = v==='none' ? '0.5' : '1';
}

function addGlobalRound(name, deptFilter){
  globalRoundCounter++;
  var rn = name||'自定义轮次';
  var rid = 'gsr_'+globalRoundCounter;
  var container = document.getElementById('gsRoundContainer');
  if(!container) return;
  var deptOpts = renderDepartmentOptions(deptFilter, false);
  var ivOpts = MOCK_INTERVIEWERS.map(function(i){
    return '<option value="'+i.id+'|'+i.name+'|'+i.dept+'">'+i.name+' · '+i.id+' · '+i.dept+'</option>';
  }).join('');
  var div = document.createElement('div');
  div.id = rid;
  div.style.cssText = 'padding:10px 12px;background:#fafbfc;border-radius:8px;border:1px solid var(--c-border)';
  div.innerHTML =
    '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">'+
      '<span style="font-size:12px;font-weight:700;color:var(--c-text);white-space:nowrap">第'+globalRoundCounter+'轮</span>'+
      '<input type="text" value="'+rn+'" class="gs-round-name" style="padding:5px 8px;border:1px solid var(--c-border);border-radius:4px;font-size:12px;width:90px">'+
      '<select class="gs-round-dept" onchange="gsPickerFilter(this)" style="padding:5px 8px;border:1px solid var(--c-border);border-radius:4px;font-size:11px;width:75px">'+
        '<option value="all">全部</option>'+deptOpts+
      '</select>'+
      '<select class="gs-round-iv" style="padding:5px 8px;border:1px solid var(--c-border);border-radius:4px;font-size:11px;flex:1;min-width:180px">'+
        '<option value="">选面试官...</option>'+ivOpts+
      '</select>'+
      '<input type="datetime-local" class="gs-round-time" style="padding:5px 8px;border:1px solid var(--c-border);border-radius:4px;font-size:11px;width:130px">'+
      '<select class="gs-round-mode" style="padding:5px 8px;border:1px solid var(--c-border);border-radius:4px;font-size:11px;width:95px">'+
        '<option>飞书视频</option><option>腾讯会议</option><option>现场</option><option>电话</option>'+
      '</select>'+
      (globalRoundCounter>1?'<button onclick="var el=document.getElementById(\''+rid+'\');if(el)el.remove();gsRenumber()" style="background:none;border:none;color:var(--c-reject);cursor:pointer;font-size:15px;padding:2px 4px">✕</button>':'')+
    '</div>';
  container.appendChild(div);
}

function gsRenumber(){
  var items = document.querySelectorAll('#gsRoundContainer > div');
  items.forEach(function(item, i){ item.querySelector('span').textContent = '第'+(i+1)+'轮'; });
  globalRoundCounter = items.length;
}

function gsPickerFilter(sel){
  var dept = sel.value;
  var ivSel = sel.parentElement.querySelector('.gs-round-iv');
  var current = ivSel.value;
  ivSel.innerHTML = '<option value="">选面试官...</option>';
  MOCK_INTERVIEWERS.forEach(function(i){
    if(dept==='all'||i.dept===dept){
      ivSel.innerHTML += '<option value="'+i.id+'|'+i.name+'|'+i.dept+'">'+i.name+' · '+i.id+' · '+i.dept+'</option>';
    }
  });
  if(current) ivSel.value = current;
}

function submitGlobalSchedule(){
  var status = document.querySelector('input[name="gsContact"]:checked');
  if(status && status.value==='none'){ alert('⚠️ 请先联系候选人确认意向后，再安排面试'); return; }
  var pos = document.getElementById('gsPosition').value;
  var candidate = document.getElementById('gsCandidate').value;
  if(!pos||!candidate){ alert('请填写候选人和岗位'); return; }
  var rounds = document.querySelectorAll('#gsRoundContainer > div');
  if(rounds.length===0){ alert('请至少配置一轮面试'); return; }
  var summary = '✅ 面试已安排！\n\n候选人：'+candidate+'\n岗位：'+pos+'\n';
  var allOk = true;
  rounds.forEach(function(r, i){
    var rname = r.querySelector('.gs-round-name').value||'第'+(i+1)+'轮';
    var iv = r.querySelector('.gs-round-iv').value;
    var time = r.querySelector('.gs-round-time').value||'待定';
    var mode = r.querySelector('.gs-round-mode').value;
    if(!iv) allOk = false;
    summary += '\n第'+(i+1)+'轮（'+rname+'）→ 面试官：'+(iv?iv.split('|')[1]:'未选择')+' · '+time+' · '+mode;
  });
  if(!allOk){ alert('请为每一轮选择面试官'); return; }
  summary += '\n\n✓ 飞书会议已自动创建\n✓ 面试邀请已发送';
  alert(summary);
  closeGlobalScheduleModal();
}

// ========== Mock 数据：候选⼈⼀键出抽屉 ==========
function mockCandidate(name){
  var mocks = {
    '张三':{name:'张三',phone:'138****1234',email:'zhangsan@example.com',education:'本科 · 计算机科学',years:'5年',company:'阿里巴巴',grade:'A',profileScore:88,profileGrade:'A',coreSkills:['Java','K8s','微服务'],matchScore:92,matchCalcDaysAgo:4,applyType:'direct',notRecommendReason:null,strengths:['Java 微服务架构能力突出','Spring Cloud 全栈经验丰富','团队管理 3 人经验'],weaknesses:['前端技术栈偏弱','英语口语一般'],salary:'¥25K-35K / 月',tags_hit:[{label:'Java',score:'95'},{label:'SpringBoot',score:'90'},{label:'K8s',score:'82'},{label:'MySQL',score:'88'}],tags_miss:[{label:'React'},{label:'TypeScript'}],eduHistory:'2015-2019 浙江大学 计算机科学 本科',workHistory:'2020-至今 阿里巴巴 Java 高级工程师\n2019-2020 杭州某创业公司 Java 开发',projectHistory:'2023 电商中台架构升级 — 主导微服务拆分，QPS 提升 3 倍\n2021 统一网关项目 — 日均处理 10 亿请求',matchRecords:[{position:'高级Java工程师',score:'92',time:'07-12',status:'通过'}],interviewRecords:[{position:'高级Java工程师',round:'初试',interviewer:'李面试官',time:'07-15 14:00',result:'待评价',resultClass:'warn'}],timeline:[{date:'07-12',desc:'邮箱采集入库',cls:'done'},{date:'07-12',desc:'AI 解析完成',cls:'done'},{date:'07-13',desc:'匹配高级Java工程师 92分',cls:'done'},{date:'07-14',desc:'HR 筛选通过',cls:'done'},{date:'07-15',desc:'安排面试',cls:'active'}],resumeUrl:'#'},
    '李四':{name:'李四',phone:'139****5678',email:'lisi@example.com',education:'硕士 · 软件工程',years:'3年',company:'腾讯',grade:'B+',profileScore:76,profileGrade:'B+',coreSkills:['Vue','React','TypeScript'],matchScore:85,matchCalcDaysAgo:5,applyType:'stock',notRecommendReason:null,strengths:['前端工程化能力强','Vue/React 双栈精通'],weaknesses:['后端经验不足','大型项目经验有限'],salary:'¥18K-25K / 月',tags_hit:[{label:'Vue',score:'88'},{label:'React',score:'85'},{label:'TypeScript',score:'80'}],tags_miss:[{label:'Node.js'}],eduHistory:'2019-2022 华中科技大学 软件工程 硕士\n2015-2019 武汉理工大学 计算机 本科',workHistory:'2022-至今 腾讯 前端工程师',projectHistory:'腾讯云控制台重构 — 主导组件库升级',matchRecords:[{position:'前端工程师',score:'85',time:'07-11',status:'通过'}],interviewRecords:[],timeline:[{date:'07-11',desc:'Boss 直聘入库',cls:'done'},{date:'07-11',desc:'AI 解析完成',cls:'done'},{date:'07-12',desc:'匹配前端工程师 85分',cls:'done'},{date:'07-14',desc:'待安排面试',cls:'active'}],resumeUrl:'#'},
    '郑一':{name:'郑一',phone:'150****9012',email:'zhengyi@example.com',education:'本科 · 电子信息',years:'4年',company:'美团',grade:'A-',profileScore:84,profileGrade:'A-',coreSkills:['React','RN','Flutter'],matchScore:88,matchCalcDaysAgo:6,applyType:'direct',notRecommendReason:null,strengths:['React Native 跨端经验丰富','性能优化能力突出'],weaknesses:['无后端背景'],salary:'¥22K-30K / 月',tags_hit:[{label:'React',score:'91'},{label:'React Native',score:'88'},{label:'Flutter',score:'76'}],tags_miss:[{label:'Vue'}],eduHistory:'2017-2021 电子科技大学 电子信息 本科',workHistory:'2021-至今 美团 前端工程师',projectHistory:'美团外卖 App RN 重构 — 首屏加载优化 40%',matchRecords:[{position:'前端工程师',score:'88',time:'07-10',status:'通过'}],interviewRecords:[{position:'前端工程师',round:'终面',interviewer:'王面试官',time:'07-12 15:00',result:'通过',resultClass:'done'}],timeline:[{date:'07-10',desc:'猎聘入库',cls:'done'},{date:'07-10',desc:'AI 解析完成',cls:'done'},{date:'07-11',desc:'匹配前端工程师 88分',cls:'done'},{date:'07-12',desc:'面试通过',cls:'done'},{date:'07-13',desc:'Offer 已发',cls:'active'}],resumeUrl:'#'},
    '孙九':{name:'孙九',phone:'186****3456',email:'sunjiu@example.com',education:'本科 · 软件工程',years:'6年',company:'字节跳动',grade:'B',profileScore:68,profileGrade:'B',coreSkills:['Go','Redis','Kafka'],matchScore:68,matchCalcDaysAgo:4,applyType:'direct',notRecommendReason:null,strengths:['Golang 后端开发经验','高并发系统设计'],weaknesses:['非 Java 技术栈（需过渡）','管理经验缺失'],salary:'¥28K-38K / 月',tags_hit:[{label:'Go',score:'90'},{label:'Redis',score:'85'},{label:'Kafka',score:'82'}],tags_miss:[{label:'Java'},{label:'SpringBoot'}],eduHistory:'2014-2018 南京大学 软件工程 本科',workHistory:'2019-至今 字节跳动 后端工程师\n2018-2019 滴滴 后端开发',projectHistory:'字节广告投放系统 — QPS 10万+ 架构设计',matchRecords:[{position:'高级Java工程师',score:'68',time:'07-13',status:'待定'}],interviewRecords:[{position:'高级Java工程师',round:'复试',interviewer:'张HR',time:'07-13 10:00',result:'待评价',resultClass:'warn'}],timeline:[{date:'07-10',desc:'内推入库',cls:'done'},{date:'07-10',desc:'AI 解析完成',cls:'done'},{date:'07-12',desc:'匹配高级Java工程师 68分',cls:'done'},{date:'07-13',desc:'复试完成待评价',cls:'active'}],resumeUrl:'#'},
    '陈二':{name:'陈二',phone:'177****7890',email:'chener@example.com',education:'本科 · 市场营销',years:'5年',company:'百度',grade:'C+',profileScore:56,profileGrade:'C+',coreSkills:['产品设计','数据分析'],matchScore:62,matchCalcDaysAgo:7,applyType:'direct',notRecommendReason:null,strengths:['产品 sense 好','数据分析能力强'],weaknesses:['技术理解力不足','B端产品经验缺失'],salary:'¥12K-18K / 月',tags_hit:[{label:'产品设计',score:'80'},{label:'数据分析',score:'78'}],tags_miss:[{label:'B端经验'},{label:'技术背景'}],eduHistory:'2015-2019 复旦大学 市场营销 本科',workHistory:'2019-至今 百度 产品经理',projectHistory:'百度搜索产品优化 — 用户留存提升 15%',matchRecords:[{position:'产品经理',score:'72',time:'07-10',status:'不通过'}],interviewRecords:[{position:'产品经理',round:'初试',interviewer:'张HR',time:'07-10 09:00',result:'不通过',resultClass:'reject'}],timeline:[{date:'07-08',desc:'邮箱入库',cls:'done'},{date:'07-08',desc:'AI 解析完成',cls:'done'},{date:'07-09',desc:'匹配产品经理 72分',cls:'done'},{date:'07-10',desc:'面试不通过',cls:'done'},{date:'07-10',desc:'简历回流人才库',cls:'active'}],resumeUrl:'#'},
    '王五':{name:'王五',phone:'133****2345',email:'wangwu@example.com',education:'硕士 · 数据科学',years:'3年',company:'网易',grade:'B+',profileScore:80,profileGrade:'B+',coreSkills:['Python','Spark','SQL'],matchScore:86,matchCalcDaysAgo:4,applyType:'stock',notRecommendReason:null,strengths:['Python/Spark 数据处理','机器学习建模经验'],weaknesses:['业务理解有待加强'],salary:'¥20K-28K / 月',tags_hit:[{label:'Python',score:'90'},{label:'Spark',score:'85'},{label:'SQL',score:'88'}],tags_miss:[{label:'Flink'}],eduHistory:'2019-2022 上海交通大学 数据科学 硕士\n2015-2019 同济大学 统计学 本科',workHistory:'2022-至今 网易 数据分析师',projectHistory:'用户增长分析平台 — 日处理 5TB 日志数据',matchRecords:[{position:'数据分析师',score:'86',time:'07-12',status:'通过'}],interviewRecords:[],timeline:[{date:'07-12',desc:'Boss 直聘入库',cls:'done'},{date:'07-12',desc:'AI 解析完成',cls:'done'},{date:'07-13',desc:'匹配数据分析师 86分',cls:'done'},{date:'07-14',desc:'待安排面试',cls:'active'}],resumeUrl:'#'},
  };
  return mocks[name] || mocks['张三'];
}

// ========== 内部员工详情抽屉 ==========
function openEmployeeDrawer(data){
  var overlay = document.getElementById('drawerOverlay');
  var drawer = document.getElementById('candidateDrawer');
  if(!overlay){
    overlay = document.createElement('div'); overlay.id='drawerOverlay'; overlay.className='drawer-overlay';
    overlay.onclick = closeCandidateDrawer;
    document.body.appendChild(overlay);
    drawer = document.createElement('div'); drawer.id='candidateDrawer'; drawer.className='drawer';
    document.body.appendChild(drawer);
  }

  var tagsHtml = (data.tags||[]).map(function(t){
    var cls = t.level>=80 ? 'tag-hit' : (t.level>=60 ? 'tag-neutral' : 'tag-miss');
    return '<span class="tag-item '+cls+'">'+t.label+'<span class="tag-score">'+t.level+'</span></span>';
  }).join('');

  var matchHtml = (data.matchHistory||[]).map(function(m){
    return '<tr><td>'+m.position+'</td><td><span style="font-weight:700;color:'+(m.score>=80?'var(--c-done)':m.score>=60?'var(--c-warn)':'var(--c-reject)')+'">'+m.score+'</span></td><td>'+m.time+'</td><td><span class="st st-'+(m.score>=80?'done':m.score>=60?'warn':'reject')+'">'+m.status+'</span></td></tr>';
  }).join('');

  var perfHtml = (data.performance||[]).map(function(p){
    return '<tr><td>'+p.period+'</td><td><span style="font-weight:700;color:'+(p.grade==='A'||p.grade==='A+'?'var(--c-done)':p.grade==='B+'||p.grade==='B'?'var(--c-progress)':'var(--c-warn)')+'">'+p.grade+'</span></td><td>'+p.comment+'</td></tr>';
  }).join('');

  var timelineHtml = (data.timeline||[]).map(function(t){
    return '<div class="tl-item '+t.cls+'"><div class="tl-date">'+t.date+'</div><div class="tl-desc">'+t.desc+'</div></div>';
  }).join('');

  var h = '';
  h += '<div class="drawer-header"><h3>👤 '+data.name+' <span style="font-size:11px;color:var(--c-sub);font-weight:400">'+data.employeeId+'</span></h3><button class="drawer-close" onclick="closeCandidateDrawer()">✕</button></div>';
  h += '<div class="drawer-body">';

  // 1. 基本信息
  h += '<div class="drawer-section"><div class="drawer-section-title">基本信息</div>';
  h += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:2px 24px">';
  h += '<div class="info-row"><span class="k">工号</span><span class="v">'+data.employeeId+'</span></div>';
  h += '<div class="info-row"><span class="k">姓名</span><span class="v">'+data.name+'</span></div>';
  h += '<div class="info-row"><span class="k">部门</span><span class="v">'+data.dept+'</span></div>';
  h += '<div class="info-row"><span class="k">岗位</span><span class="v">'+data.position+'</span></div>';
  h += '<div class="info-row"><span class="k">入职日期</span><span class="v">'+data.entryDate+'</span></div>';
  h += '<div class="info-row"><span class="k">工龄</span><span class="v">'+data.tenure+'</span></div>';
  h += '<div class="info-row"><span class="k">性别</span><span class="v">'+data.gender+'</span></div>';
  h += '<div class="info-row"><span class="k">出生日期</span><span class="v">'+data.birth+'</span></div>';
  h += '<div class="info-row"><span class="k">手机</span><span class="v">'+data.phone+'</span></div>';
  h += '<div class="info-row"><span class="k">邮箱</span><span class="v">'+data.email+'</span></div>';
  h += '<div class="info-row"><span class="k">企业微信</span><span class="v">'+data.wecom+'</span></div>';
  h += '<div class="info-row"><span class="k">直属上级</span><span class="v">'+data.manager+'</span></div>';
  h += '</div></div>';

  // 2. 学历 & 资质
  h += '<div class="drawer-section"><div class="drawer-section-title">学历 & 资质</div>';
  h += '<div class="accordion open"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">🎓 教育背景 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+data.education+'</div></div>';
  h += '<div class="accordion"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">📜 证书 & 资质 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.certificates||[]).map(function(c){return '<div style="font-size:12px;line-height:2">• <b>'+c.name+'</b> — '+c.date+(c.expiry?'（有效期至 '+c.expiry+'）':'')+'</div>';}).join('')+'</div></div>';
  h += '</div>';

  // 3. 绩效记录
  h += '<div class="drawer-section"><div class="drawer-section-title">绩效记录</div>';
  h += '<table style="font-size:12px"><thead><tr><th>考核周期</th><th>评级</th><th>评语</th></tr></thead><tbody>'+perfHtml+'</tbody></table>';
  h += '</div>';

  // 4. 技能标签
  h += '<div class="drawer-section"><div class="drawer-section-title">技能标签</div>';
  h += '<div class="tag-cloud">'+(tagsHtml||'<span style="font-size:12px;color:var(--c-sub)">暂无标签</span>')+'</div>';
  h += '<div style="font-size:11px;color:var(--c-sub);margin-top:6px">标签来源于简历解析 + 历史项目 + 面试评价 + 培训记录，定期更新</div>';
  h += '</div>';

  // 5. 岗位匹配历史
  h += '<div class="drawer-section"><div class="drawer-section-title">内部匹配历史</div>';
  h += '<table style="font-size:12px"><thead><tr><th>岗位</th><th>匹配分</th><th>匹配时间</th><th>状态</th></tr></thead><tbody>'+(matchHtml||'<tr><td colspan="4" style="color:var(--c-sub);text-align:center">暂无匹配记录</td></tr>')+'</tbody></table>';
  h += '</div>';

  // 6. 调岗信息
  h += '<div class="drawer-section"><div class="drawer-section-title">调岗状态</div>';
  h += '<div class="portrait-card">';
  h += '<div style="display:flex;align-items:center;justify-content:space-between">';
  h += '<div><div style="font-size:20px;font-weight:900;color:'+(data.transferable?'var(--c-done)':'var(--c-reject)')+'">'+(data.transferable?'✅ 可调岗':'❌ 不可调岗')+'</div><div style="font-size:11px;color:var(--c-sub)">'+data.transferReason+'</div></div>';
  if(data.transferable){
    h += '<div style="text-align:right"><div style="font-size:11px;color:var(--c-sub)">最早可调日期</div><div style="font-size:14px;font-weight:700">'+data.transferDate+'</div></div>';
  }
  h += '</div></div></div>';

  // 7. 职业发展
  h += '<div class="drawer-section"><div class="drawer-section-title">职业发展</div>';
  h += '<div class="accordion open"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">📈 晋升记录 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.promotions||[]).map(function(p){return '<div style="font-size:12px;line-height:2">• <b>'+p.date+'</b> '+p.from+' → '+p.to+'</div>';}).join('')+'</div></div>';
  h += '<div class="accordion"><div class="accordion-header" onclick="this.parentElement.classList.toggle(\'open\')">📚 培训记录 <span class="accordion-arrow">▾</span></div><div class="accordion-body">'+(data.trainings||[]).map(function(t){return '<div style="font-size:12px;line-height:2">• <b>'+t.date+'</b> '+t.name+' — '+t.result+'</div>';}).join('')+'</div></div>';
  h += '</div>';

  // 8. 在职时间线
  h += '<div class="drawer-section"><div class="drawer-section-title">在职时间线</div>';
  h += '<div class="timeline">'+timelineHtml+'</div>';
  h += '</div>';

  h += '</div>';
  drawer.innerHTML = h;

  overlay.classList.add('open');
  drawer.classList.add('open');
  document.body.style.overflow = 'hidden';
}

// ========== Mock 数据：内部员⼯ ==========
function mockEmployee(name){
  var mocks = {
    '王工':{
      employeeId:'EMP001', name:'王工', dept:'技术部', position:'高级Java工程师',
      entryDate:'2023-03-15', tenure:'3年4个月', gender:'男', birth:'1995-08-22',
      phone:'156****8901', email:'wanggong@company.com', wecom:'王工(WangGong)',
      manager:'技术总监-刘博',
      education:'2017-2020 浙江大学 计算机科学 硕士\n2013-2017 杭州电子科技大学 软件工程 本科',
      certificates:[
        {name:'AWS Solutions Architect Professional', date:'2025-06', expiry:'2028-06'},
        {name:'CKAD (Kubernetes Application Developer)', date:'2024-12', expiry:'2027-12'},
        {name:'PMP 项目管理认证', date:'2025-03', expiry:'2028-03'}
      ],
      performance:[
        {period:'2026 H1', grade:'A', comment:'主导微服务架构升级，系统吞吐量提升 3 倍，获技术委员会年度最佳项目奖'},
        {period:'2025 H2', grade:'A', comment:'完成统一网关项目核心模块，日均处理 10 亿请求零故障'},
        {period:'2025 H1', grade:'B+', comment:'按时交付 3 个 Sprint 目标，代码质量评分 92/100'}
      ],
      tags:[
        {label:'Java',level:95},{label:'Spring Boot',level:92},{label:'Spring Cloud',level:88},
        {label:'Kubernetes',level:85},{label:'Docker',level:82},{label:'MySQL',level:88},
        {label:'Redis',level:85},{label:'Kafka',level:78},{label:'Elasticsearch',level:72},
        {label:'Linux',level:80},{label:'Git',level:88},{label:'Jenkins',level:75},
        {label:'微服务架构',level:90},{label:'系统设计',level:88},{label:'性能优化',level:85}
      ],
      matchHistory:[
        {position:'高级Java工程师（架构方向）', score:92, time:'2026-07-12', status:'匹配通过'},
        {position:'技术经理（储备）', score:78, time:'2026-06-20', status:'关注中'},
        {position:'资深后端工程师', score:85, time:'2026-05-08', status:'已面试'}
      ],
      transferable:true, transferDate:'2026-09-01', transferReason:'当前项目收尾阶段，需完成交接后可调岗',
      promotions:[
        {date:'2025-07', from:'Java工程师', to:'高级Java工程师'},
        {date:'2023-03', from:'—', to:'Java工程师（入职）'}
      ],
      trainings:[
        {date:'2026-04', name:'系统架构设计高级研修班', result:'结业·优秀'},
        {date:'2025-10', name:'技术管理能力提升培训', result:'结业'},
        {date:'2024-06', name:'AWS 云架构认证培训', result:'通过认证'}
      ],
      timeline:[
        {date:'2023-03-15', desc:'入职 — Java工程师', cls:'done'},
        {date:'2024-06', desc:'通过 AWS 架构师认证', cls:'done'},
        {date:'2025-07', desc:'晋升高级Java工程师', cls:'done'},
        {date:'2025-10', desc:'完成技术管理培训', cls:'done'},
        {date:'2026-07', desc:'内部匹配 — 高级Java架构方向 92分', cls:'active'}
      ]
    },
    '赵工':{
      employeeId:'EMP015', name:'赵工', dept:'数据部', position:'数据分析师',
      entryDate:'2024-06-01', tenure:'2年1个月', gender:'女', birth:'1997-03-10',
      phone:'177****2345', email:'zhaogong@company.com', wecom:'赵工(ZhaoGong)',
      manager:'数据总监-陈博',
      education:'2021-2024 复旦大学 应用统计 硕士\n2017-2021 华东师范大学 统计学 本科',
      certificates:[
        {name:'Google Data Analytics Professional', date:'2023-08'},
        {name:'Tableau Desktop Specialist', date:'2024-03'}
      ],
      performance:[
        {period:'2026 H1', grade:'B+', comment:'独立完成用户增长分析平台，日处理 5TB 数据，输出 3 份核心业务报告'},
        {period:'2025 H2', grade:'B', comment:'配合完成数据仓库迁移，如期交付'}
      ],
      tags:[
        {label:'Python',level:88},{label:'SQL',level:85},{label:'Spark',level:72},
        {label:'Tableau',level:78},{label:'数据分析',level:82},{label:'统计学',level:80},
        {label:'机器学习',level:65},{label:'ETL',level:70}
      ],
      matchHistory:[
        {position:'高级数据分析师', score:65, time:'2026-07-10', status:'未通过·经验不足'}
      ],
      transferable:false, transferDate:'—', transferReason:'入职不满 2 年，暂不满足内部调岗最低年限要求',
      promotions:[
        {date:'2024-06', from:'—', to:'数据分析师（入职）'}
      ],
      trainings:[
        {date:'2025-08', name:'Spark 大数据处理实战培训', result:'结业'},
        {date:'2024-10', name:'数据可视化与 Storytelling', result:'结业'}
      ],
      timeline:[
        {date:'2024-06-01', desc:'入职 — 数据分析师', cls:'done'},
        {date:'2025-08', desc:'完成 Spark 大数据培训', cls:'done'},
        {date:'2026-07', desc:'内部匹配 — 高级数据分析师 65分（未通过）', cls:'active'}
      ]
    },
    '钱工':{
      employeeId:'EMP023', name:'钱工', dept:'产品部', position:'高级产品经理',
      entryDate:'2021-09-01', tenure:'4年10个月', gender:'男', birth:'1992-11-05',
      phone:'188****6789', email:'qiangong@company.com', wecom:'钱工(QianGong)',
      manager:'产品VP-周博',
      education:'2014-2017 上海交通大学 工商管理 硕士\n2010-2014 南京大学 信息管理 本科',
      certificates:[
        {name:'NPDP 产品经理国际认证', date:'2024-05', expiry:'2027-05'},
        {name:'PMP 项目管理认证', date:'2023-09', expiry:'2026-09'}
      ],
      performance:[
        {period:'2026 H1', grade:'A+', comment:'主导 SaaS 产品线从 0 到 1，半年 ARR 突破 500 万，获年度最佳产品奖'},
        {period:'2025 H2', grade:'A', comment:'完成 3 个核心功能模块上线，NPS 提升 15 个百分点'},
        {period:'2025 H1', grade:'A', comment:'重构产品需求管理流程，需求交付效率提升 40%'}
      ],
      tags:[
        {label:'B端产品设计',level:92},{label:'SaaS',level:90},{label:'数据分析',level:85},
        {label:'用户研究',level:88},{label:'敏捷开发',level:85},{label:'竞品分析',level:82},
        {label:'需求管理',level:90},{label:'产品规划',level:88},{label:'SQL',level:70},
        {label:'Figma',level:78},{label:'项目管理',level:85}
      ],
      matchHistory:[
        {position:'产品总监（储备）', score:88, time:'2026-07-08', status:'匹配通过·关注中'},
        {position:'高级产品经理（B端）', score:95, time:'2026-06-15', status:'已面试·通过'}
      ],
      transferable:true, transferDate:'2026-10-01', transferReason:'当前 SaaS 产品线需要完成 Q3 里程碑后交接',
      promotions:[
        {date:'2023-07', from:'产品经理', to:'高级产品经理'},
        {date:'2021-09', from:'—', to:'产品经理（入职）'}
      ],
      trainings:[
        {date:'2026-02', name:'产品战略与商业思维高级研修', result:'结业·优秀'},
        {date:'2024-12', name:'SaaS 增长方法论实战工作坊', result:'结业'},
        {date:'2023-05', name:'PMP 项目管理认证培训', result:'通过认证'}
      ],
      timeline:[
        {date:'2021-09-01', desc:'入职 — 产品经理', cls:'done'},
        {date:'2023-05', desc:'通过 PMP 认证', cls:'done'},
        {date:'2023-07', desc:'晋升高级产品经理', cls:'done'},
        {date:'2026-07', desc:'内部匹配 — 产品总监方向 88分', cls:'active'}
      ]
    }
  };
  return mocks[name] || mocks['王工'];
}

// ========== 重新匹配弹窗（需求详情页入口） ==========
function openRematchModal(demandId){
  var old = document.getElementById('rematchModal');
  if(old) old.remove();
  var overlay = document.createElement('div');
  overlay.id = 'rematchModal';
  overlay.className = 'modal-overlay';
  overlay.style.display = 'flex';
  overlay.onclick = function(e){ if(e.target===overlay) closeRematchModal(); };
  var html = '<div class="modal-box" style="width:480px"><h3>🔄 重新匹配</h3>';
  html += '<div style="font-size:13px;color:var(--c-sub);margin-bottom:16px">基于当前最新 JD 和最新简历，重新计算岗位匹配分。旧匹配记录将被覆盖。</div>';
  html += '<div class="checkbox-group" style="margin-bottom:16px">';
  html += '<label><input type="checkbox" id="rmExternal" checked> ☑ 外部简历库（默认勾选）</label>';
  html += '<label><input type="checkbox" id="rmInternal"> ☐ 内部员工库（默认不勾选）</label>';
  html += '</div>';
  html += '<div class="form-group" style="margin-bottom:12px"><label>简历检索时长</label><select id="rmRange"><option>近 3 个月</option><option>近 6 个月</option><option>全部</option></select></div>';
  html += '<div class="modal-actions">';
  html += '<button class="btn btn-ghost btn-sm" onclick="closeRematchModal()">取消</button>';
  html += '<button class="btn btn-primary btn-sm" onclick="submitRematch(\''+(demandId||'DM2026070005')+'\')">开始重新匹配</button>';
  html += '</div></div>';
  overlay.innerHTML = html;
  document.body.appendChild(overlay);
}
function closeRematchModal(){ var el = document.getElementById('rematchModal'); if(el) el.remove(); }
function submitRematch(demandId){
  var ext = document.getElementById('rmExternal').checked;
  var int = document.getElementById('rmInternal').checked;
  var range = document.getElementById('rmRange').value;
  if(!ext && !int){ alert('未选择任何匹配范围，操作取消'); closeRematchModal(); return; }
  alert('🔄 重新匹配已启动！\n\n需求单号：'+demandId+'\n外部简历：'+(ext?'✅':'❌')+' · 内部员工：'+(int?'✅':'❌')+'\n检索范围：'+range+'\n\n匹配完成后将刷新页面。');
  closeRematchModal();
  location.reload();
}

// ========== 统一操作按钮生成器（全局强制） ==========
// 每个候选人行固定 3 个操作位：查看 | 联系 | 约面
// state: 'interviewing'|'screening'|'contacted'|'no_intent'|'disqualified'|'archived'
function renderActionButtons(candidateName, state, position, dept, failReason){
  var view  = '<button class="btn btn-outline btn-sm" onclick="openCandidateDrawer(mockCandidate(\''+candidateName+'\'))">查看</button>';
  var call  = '';
  var meet  = '';
  switch(state){
    case 'screening': // 待筛选 → 先联系确认，约面不可用
      call = '<button class="btn btn-primary btn-sm" onclick="openContactModal(\''+candidateName+'\')">联系</button>';
      meet = '<button class="btn btn-ghost btn-sm">约面</button>';
      break;
    case 'contacted': // 已联系确认 → 约面可用
      call = '<span class="btn btn-text btn-sm" style="color:var(--c-done);font-weight:700">✓ 已联系</span>';
      meet = '<button class="btn btn-primary btn-sm" onclick="openGlobalScheduleModal(\''+candidateName+'\',\''+(position||'')+'\',\''+(dept||'')+'\')">约面</button>';
      break;
    case 'interviewing': // 面试中 → 都不可用
      call = '<button class="btn btn-ghost btn-sm">联系</button>';
      meet = '<button class="btn btn-ghost btn-sm">约面</button>';
      break;
    case 'no_intent': // 无意向
      call = '<button class="btn btn-ghost btn-sm">联系</button>';
      meet = '<button class="btn btn-ghost btn-sm">约面 <span style="font-size:10px;color:var(--c-sub)">婉拒</span></button>';
      break;
    case 'disqualified': // 条件不符
      call = '<button class="btn btn-ghost btn-sm">联系</button>';
      meet = '<button class="btn btn-ghost btn-sm">约面 <span style="font-size:10px;color:var(--c-sub)">'+(failReason||'不符')+'</span></button>';
      break;
    default:
      call = '<button class="btn btn-ghost btn-sm">联系</button>';
      meet = '<button class="btn btn-ghost btn-sm">约面</button>';
  }
  return view + ' ' + call + ' ' + meet;
}

// ========== 联系候选人弹窗（多方式选择） ==========
function openContactModal(name){
  var old = document.getElementById('contactModal');
  if(old) old.remove();
  var overlay = document.createElement('div');
  overlay.id = 'contactModal';
  overlay.className = 'modal-overlay';
  overlay.style.display = 'flex';
  overlay.onclick = function(e){ if(e.target===overlay) closeContactModal(); };
  var html = '<div class="modal-box" style="width:400px"><h3>📞 联系 '+name+'</h3>';
  html += '<div style="font-size:13px;color:var(--c-sub);margin-bottom:16px">选择联系方式，记录联系结果</div>';
  html += '<div style="display:flex;flex-direction:column;gap:8px;margin-bottom:16px">';
  html += '<button class="btn btn-primary" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'已生成联系话术草稿：\\n\\n您好，我是招聘团队。看到您与当前岗位较匹配，想和您确认近期机会意向与可面试时间。\\n\\n请通过电话、邮件或飞书人工确认后再约面。\');closeContactModal()">联系话术草稿</button>';
  html += '<button class="btn btn-outline" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'📱 已记录：电话联系 '+name+'\');closeContactModal()">📱 电话联系</button>';
  html += '<button class="btn btn-outline" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'📧 已记录：邮件联系 '+name+'\');closeContactModal()">📧 邮件联系</button>';
  html += '<button class="btn btn-outline" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'💬 已记录：飞书消息联系 '+name+'\');closeContactModal()">💬 飞书消息</button>';
  html += '</div>';
  html += '<div class="modal-actions"><button class="btn btn-ghost btn-sm" onclick="closeContactModal()">取消</button></div>';
  html += '</div>';
  overlay.innerHTML = html;
  document.body.appendChild(overlay);
}
function closeContactModal(){ var el = document.getElementById('contactModal'); if(el) el.remove(); }

// 内部员工联系弹窗（含直属上级）
function openInternalContactModal(name, manager){
  var old = document.getElementById('contactModal');
  if(old) old.remove();
  var overlay = document.createElement('div');
  overlay.id = 'contactModal';
  overlay.className = 'modal-overlay';
  overlay.style.display = 'flex';
  overlay.onclick = function(e){ if(e.target===overlay) closeContactModal(); };
  var html = '<div class="modal-box" style="width:400px"><h3>📞 联系 '+name+'</h3>';
  html += '<div style="font-size:13px;color:var(--c-sub);margin-bottom:16px">内部调岗需先沟通员工本人及直属上级 '+manager+'</div>';
  html += '<div style="display:flex;flex-direction:column;gap:8px;margin-bottom:16px">';
  html += '<button class="btn btn-primary" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'📱 已记录：电话联系 '+name+'\');closeContactModal()">📱 电话联系本人</button>';
  html += '<button class="btn btn-outline" style="justify-content:flex-start;padding:12px 16px" onclick="alert(\'💬 已记录：飞书联系 '+name+'\');closeContactModal()">💬 飞书联系本人</button>';
  html += '<button class="btn btn-outline" style="justify-content:flex-start;padding:12px 16px;border-color:var(--c-warn);color:var(--c-warn)" onclick="alert(\'👔 已记录：联系直属上级 '+manager+'\n\\n调岗需上级确认同意\');closeContactModal()">👔 联系直属上级 '+manager+'</button>';
  html += '</div>';
  html += '<div class="modal-actions"><button class="btn btn-ghost btn-sm" onclick="closeContactModal()">取消</button></div>';
  html += '</div>';
  overlay.innerHTML = html;
  document.body.appendChild(overlay);
}

// ========== 登录保护 ==========
(function(){
  if(location.pathname.indexOf('/login')===-1 && !localStorage.getItem('hr_role')){
    if(window.__legacyNavigate){ window.__legacyNavigate('/login'); }
    else { location.href = '/login'; }
  }
})();

// Enterprise shell and command palette.
(function(){
  if(location.pathname.indexOf('/login') !== -1) return;
  var commands = [
    {id:'recruit-dashboard', label:'招聘看板', hint:'查看漏斗、KPI 与风险提醒', href:'/recruit-dashboard'},
    {id:'recruit-demand', label:'需求管理', hint:'筛选、创建和审批招聘需求', href:'/recruit-demand'},
    {id:'recruit-demand-detail', label:'需求详情', hint:'查看岗位候选人、批量操作与匹配记录', href:'/recruit-demand-detail', parent:'recruit-demand'},
    {id:'recruit-talent', label:'人才库', hint:'检索候选人、联系记录与人才标签', href:'/recruit-talent'},
    {id:'recruit-interview', label:'面试计划', hint:'安排面试、查看日程和评价状态', href:'/recruit-interview'},
    {id:'recruit-ai', label:'招聘辅助中心', hint:'候选人沟通辅助、简历摘要与效率分析', href:'/recruit-ai'},
    {id:'recruit-config', label:'基础配置', hint:'维护部门、流程、通知和系统规则', href:'/recruit-config'}
  ];

  function go(href){
    if(window.__legacyNavigate) window.__legacyNavigate(href);
    else location.href = href;
  }

  function currentRouteId(){
    return location.pathname.replace(/^\//,'') || 'recruit-dashboard';
  }

  function allowedCommands(){
    var visibleIds = getVisibleMenus(getRole()).map(function(item){ return item.id; });
    return commands.filter(function(item){
      return visibleIds.indexOf(item.id) !== -1 || (item.parent && visibleIds.indexOf(item.parent) !== -1);
    });
  }

  function ensureTopbarActions(){
    var topbar = document.querySelector('.topbar');
    if(!topbar) return null;
    topbar.setAttribute('role','banner');
    var spacer = topbar.querySelector('.spacer');
    if(!spacer) return null;
    var actions = topbar.querySelector('.topbar-actions');
    if(!actions){
      actions = document.createElement('div');
      actions.className = 'topbar-actions';
      spacer.parentNode.insertBefore(actions, spacer.nextSibling);
      var node = actions.nextSibling;
      while(node){
        var next = node.nextSibling;
        actions.appendChild(node);
        node = next;
      }
    }
    return actions;
  }

  function enhanceWorkbenchShell(){
    document.body.classList.add('enterprise-workbench');
    document.body.setAttribute('data-route', currentRouteId());

    var content = document.querySelector('.content');
    if(content) content.classList.add('workbench-ready');

    var main = document.querySelector('.content-body');
    if(main){
      main.setAttribute('role','main');
      main.setAttribute('tabindex','-1');
    }

    var sidebar = document.getElementById('sidebar');
    if(sidebar){
      sidebar.setAttribute('role','navigation');
      sidebar.setAttribute('aria-label','招聘模块导航');
      Array.prototype.forEach.call(sidebar.querySelectorAll('a'), function(link){
        if(link.classList.contains('active')) link.setAttribute('aria-current','page');
        if(!link.getAttribute('title')) link.setAttribute('title', link.textContent.trim());
      });
    }

    ensureTopbarActions();
  }

  function textOf(el){
    return (el && el.textContent ? el.textContent : '').replace(/\s+/g,' ').trim();
  }

  function dispatchNative(el, type){
    el.dispatchEvent(new Event(type, { bubbles:true }));
  }

  function enhanceMetricCards(){
    Array.prototype.forEach.call(document.querySelectorAll('.metric-card'), function(card){
      if(card.dataset.coreEnhanced === 'true') return;
      card.dataset.coreEnhanced = 'true';
      var value = textOf(card.querySelector('.metric-value'));
      var label = textOf(card.querySelector('.metric-label'));
      card.setAttribute('role','group');
      card.setAttribute('aria-label', (label || 'KPI') + (value ? '，当前值 ' + value : ''));
      if(!card.querySelector('.metric-window')){
        var target = card.querySelector('.metric-label');
        if(target){
          var meta = document.createElement('div');
          meta.className = 'metric-window';
          meta.textContent = '当前筛选范围';
          target.insertAdjacentElement('afterend', meta);
        }
      }
    });
  }

  function enhanceStatusLabels(){
    Array.prototype.forEach.call(document.querySelectorAll('.st,.role-badge,.tag-item,.tag-fold,.phase-badge'), function(item){
      if(!item.getAttribute('title')) item.setAttribute('title', textOf(item));
    });
  }

  function enhanceFilterBars(){
    Array.prototype.forEach.call(document.querySelectorAll('.filter-bar'), function(bar, barIndex){
      if(bar.dataset.coreEnhanced === 'true') return;
      bar.dataset.coreEnhanced = 'true';
      bar.classList.add('component-filter-bar');
      bar.setAttribute('role','search');
      bar.setAttribute('aria-label','筛选条件');

      var controls = Array.prototype.filter.call(bar.querySelectorAll('input,select'), function(control){
        return control.type !== 'hidden';
      });
      controls.forEach(function(control, index){
        var label = control.getAttribute('placeholder') || control.getAttribute('aria-label') || textOf(control.previousElementSibling) || ('筛选项 ' + (index + 1));
        control.setAttribute('aria-label', label);
        if(!control.id) control.id = 'filterControl_' + barIndex + '_' + index;
      });

      if(controls.length && !bar.querySelector('.filter-reset')){
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-ghost btn-sm filter-reset';
        btn.textContent = '清空筛选';
        btn.onclick = function(){
          controls.forEach(function(control){
            if(control.tagName === 'SELECT'){
              var all = Array.prototype.find.call(control.options, function(option){ return option.value === 'all'; });
              control.value = all ? 'all' : (control.options[0] ? control.options[0].value : '');
              dispatchNative(control, 'change');
            } else if(control.type === 'checkbox' || control.type === 'radio'){
              control.checked = false;
              dispatchNative(control, 'change');
            } else {
              control.value = '';
              dispatchNative(control, 'input');
              dispatchNative(control, 'change');
            }
          });
        };
        bar.appendChild(btn);
      }
    });
  }

  function comparableValue(text){
    var cleaned = text.replace(/[¥,%\s]/g,'').replace(/K$/i,'000');
    var number = parseFloat(cleaned);
    return isNaN(number) ? text.toLowerCase() : number;
  }

  function sortTable(table, index, direction){
    var tbody = table.tBodies[0];
    if(!tbody) return;
    var rows = Array.prototype.filter.call(tbody.rows, function(row){ return row.cells.length > index && !row.classList.contains('table-empty-row'); });
    rows.sort(function(a,b){
      var av = comparableValue(textOf(a.cells[index]));
      var bv = comparableValue(textOf(b.cells[index]));
      if(typeof av === 'number' && typeof bv === 'number') return direction === 'asc' ? av - bv : bv - av;
      return direction === 'asc' ? String(av).localeCompare(String(bv), 'zh-Hans-CN') : String(bv).localeCompare(String(av), 'zh-Hans-CN');
    });
    rows.forEach(function(row){ tbody.appendChild(row); });
  }

  function enhanceTables(){
    Array.prototype.forEach.call(document.querySelectorAll('table'), function(table, tableIndex){
      if(table.dataset.coreEnhanced !== 'true'){
        table.dataset.coreEnhanced = 'true';
        table.classList.add('data-table');
        table.setAttribute('role','table');
      }

      var wrap = table.closest('.table-wrap');
      if(wrap && wrap.dataset.coreEnhanced !== 'true'){
        wrap.dataset.coreEnhanced = 'true';
        wrap.classList.add('component-table');
        wrap.setAttribute('role','region');
        wrap.setAttribute('tabindex','0');
        wrap.setAttribute('aria-label', textOf(table.querySelector('th')) || '数据表格');
        wrap.setAttribute('data-density', localStorage.getItem('hr_table_density') || 'standard');

        var density = document.createElement('div');
        density.className = 'table-density';
        density.setAttribute('aria-label','表格密度');
        ['compact','standard','comfortable'].forEach(function(mode){
          var btn = document.createElement('button');
          btn.type = 'button';
          btn.dataset.density = mode;
          btn.textContent = mode === 'compact' ? '紧凑' : (mode === 'comfortable' ? '舒适' : '标准');
          btn.onclick = function(){
            wrap.setAttribute('data-density', mode);
            localStorage.setItem('hr_table_density', mode);
          };
          density.appendChild(btn);
        });
        wrap.insertBefore(density, table);
      }

      var headers = table.tHead ? Array.prototype.slice.call(table.tHead.querySelectorAll('th')) : [];
      headers.forEach(function(th, index){
        if(th.dataset.sortEnhanced === 'true') return;
        if(!textOf(th) || /操作|选择/.test(textOf(th)) || th.querySelector('input,button,select')) return;
        th.dataset.sortEnhanced = 'true';
        th.classList.add('sortable-th');
        th.setAttribute('tabindex','0');
        th.setAttribute('role','button');
        th.setAttribute('aria-sort','none');
        th.setAttribute('title','点击排序');
        function toggleSort(){
          var next = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';
          headers.forEach(function(item){ item.setAttribute('aria-sort','none'); });
          th.setAttribute('aria-sort', next);
          sortTable(table, index, next === 'ascending' ? 'asc' : 'desc');
        }
        th.addEventListener('click', toggleSort);
        th.addEventListener('keydown', function(e){
          if(e.key === 'Enter' || e.key === ' '){ e.preventDefault(); toggleSort(); }
        });
      });

      var tbody = table.tBodies[0];
      if(tbody && tbody.rows.length === 0 && table.dataset.emptyInjected !== 'true'){
        table.dataset.emptyInjected = 'true';
        var row = tbody.insertRow();
        row.className = 'table-empty-row';
        var cell = row.insertCell();
        cell.colSpan = Math.max(headers.length, 1);
        cell.innerHTML = '<div class="table-empty-state"><strong>暂无匹配数据</strong><span>调整筛选条件后可重新查看结果。</span></div>';
      }
    });
  }

  function enhanceBatchBars(){
    Array.prototype.forEach.call(document.querySelectorAll('.batch-bar'), function(bar){
      bar.classList.add('component-batch-bar');
      bar.setAttribute('role','status');
      bar.setAttribute('aria-live','polite');
    });
  }

  function enhanceDialogs(){
    Array.prototype.forEach.call(document.querySelectorAll('.drawer,.modal-box,.command-palette'), function(panel){
      if(panel.dataset.dialogEnhanced === 'true') return;
      panel.dataset.dialogEnhanced = 'true';
      panel.setAttribute('role','dialog');
      panel.setAttribute('aria-modal','true');
      panel.setAttribute('tabindex','-1');
      var title = panel.querySelector('h3,.drawer-header h3,.card-title');
      if(title && !panel.getAttribute('aria-label')) panel.setAttribute('aria-label', textOf(title));
    });
  }

  function enhanceEmptyStates(){
    Array.prototype.forEach.call(document.querySelectorAll('.empty-state,.placeholder-page'), function(el){
      if(el.dataset.coreEnhanced === 'true') return;
      el.dataset.coreEnhanced = 'true';
      el.setAttribute('role','status');
      if(!el.querySelector('strong') && textOf(el)){
        el.innerHTML = '<strong>'+textOf(el)+'</strong><span>当前没有可展示的数据，请检查筛选条件或稍后重试。</span>';
      }
    });
  }

  function enhanceVisualizationCards(){
    Array.prototype.forEach.call(document.querySelectorAll('.card'), function(card){
      var title = textOf(card.querySelector('.card-title,.collapse-toggle .card-title'));
      if(!title) return;
      if(/漏斗|进度|统计|渠道|趋势|看板/.test(title)){
        card.classList.add('component-viz-card');
        card.setAttribute('role','region');
        card.setAttribute('aria-label', title);
      }
      if(/招聘全漏斗/.test(title) && card.dataset.vizEnhanced !== 'funnel'){
        card.dataset.vizEnhanced = 'funnel';
        var funnel = Array.prototype.find.call(card.querySelectorAll('div'), function(el){
          return el.children.length >= 4 && /收简历/.test(textOf(el));
        });
        if(funnel){
          funnel.classList.add('viz-funnel');
          Array.prototype.forEach.call(funnel.children, function(step, index){
            step.classList.add('viz-funnel-step');
            step.setAttribute('role','link');
            step.setAttribute('tabindex','0');
            step.setAttribute('aria-label', '查看漏斗阶段：' + textOf(step));
            step.addEventListener('keydown', function(e){
              if((e.key === 'Enter' || e.key === ' ') && typeof step.onclick === 'function'){
                e.preventDefault();
                step.onclick();
              }
            });
            step.style.setProperty('--viz-index', index);
          });
        }
      }
      if(/渠道效果统计/.test(title)){
        card.classList.add('component-chart-table');
      }
      if(card.classList.contains('component-viz-card') && !card.querySelector('.viz-card-meta')){
        var titleEl = card.querySelector('.card-title,.collapse-toggle');
        var meta = document.createElement('span');
        meta.className = 'viz-card-meta';
        meta.textContent = /渠道/.test(title) ? '按渠道汇总' : (/漏斗/.test(title) ? '阶段转化' : '当前周期');
        if(titleEl) titleEl.appendChild(meta);
      }
    });
  }

  function ensureHeroModuleTabs(){
    var body = document.querySelector('.content-body');
    if(!body || body.querySelector('.hero-module-tabs')) return;
    var visible = getVisibleMenus(getRole());
    if(!visible.length) return;
    var current = currentRouteId();
    var tabs = document.createElement('nav');
    tabs.className = 'hero-module-tabs';
    tabs.setAttribute('aria-label','招聘业务模块');
    tabs.innerHTML = visible.map(function(item){
      var active = item.id === current || (current === 'recruit-demand-detail' && item.id === 'recruit-demand');
      return '<a class="hero-module-tab'+(active?' active':'')+'" href="'+item.href+'"'+(active?' aria-current="page"':'')+'><span class="hero-module-dot"></span>'+item.label+'</a>';
    }).join('');
    body.insertBefore(tabs, body.firstChild);
  }

  function ensureHeroDashboardAnalytics(){
    if(currentRouteId() !== 'recruit-dashboard') return;
    var body = document.querySelector('.content-body');
    var kpiRow = document.getElementById('kpiRow');
    if(!body || !kpiRow || body.querySelector('.hero-analytics-grid')) return;
    var panel = document.createElement('section');
    panel.className = 'hero-analytics-grid';
    panel.innerHTML =
      '<article class="hero-chart-card hero-chart-card--wide" aria-label="招聘趋势">' +
        '<div class="hero-card-head"><div><strong>招聘趋势</strong><span>简历、面试、入职 · 过去 10 天</span></div><div class="hero-card-tabs"><button class="active">日</button><button>周</button><button>月</button></div></div>' +
        '<svg class="hero-line-chart" viewBox="0 0 720 220" role="img" aria-label="招聘趋势折线图">' +
          '<defs><linearGradient id="heroLineFill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stop-color="#7C5CFF" stop-opacity=".22"/><stop offset="100%" stop-color="#7C5CFF" stop-opacity="0"/></linearGradient></defs>' +
          '<g class="grid"><path d="M40 40H700M40 90H700M40 140H700M40 190H700"/></g>' +
          '<path class="area" d="M40 150 C100 124 134 128 182 104 S274 70 328 118 S418 160 482 98 S592 74 700 118 L700 198 L40 198 Z"/>' +
          '<path class="line purple" d="M40 150 C100 124 134 128 182 104 S274 70 328 118 S418 160 482 98 S592 74 700 118"/>' +
          '<path class="line amber" d="M40 132 C112 146 162 124 222 88 S316 142 384 122 S500 68 570 110 S646 140 700 122"/>' +
          '<g class="axis"><text x="40" y="214">07-08</text><text x="190" y="214">07-11</text><text x="340" y="214">07-14</text><text x="510" y="214">07-17</text><text x="660" y="214">07-20</text></g>' +
        '</svg>' +
        '<div class="hero-chart-legend"><span><i class="purple"></i>简历量 346</span><span><i class="amber"></i>面试量 42</span></div>' +
      '</article>' +
      '<article class="hero-chart-card" aria-label="候选人分布">' +
        '<div class="hero-card-head"><div><strong>候选人分布</strong><span>按状态拆分</span></div></div>' +
        '<div class="hero-donut-wrap"><div class="hero-donut" aria-hidden="true"></div><div class="hero-donut-center"><strong>346</strong><span>候选人</span></div></div>' +
        '<div class="hero-donut-list"><span><i class="green"></i>可推进 55%</span><span><i class="purple"></i>待评估 25%</span><span><i class="amber"></i>需跟进 20%</span></div>' +
      '</article>';
    kpiRow.insertAdjacentElement('afterend', panel);
  }

  function ensureHeroPageSummary(){
    var route = currentRouteId();
    if(route === 'recruit-dashboard' || route === 'login') return;
    var body = document.querySelector('.content-body');
    if(!body || body.querySelector('.hero-page-summary')) return;
    var presets = {
      'recruit-demand': [
        ['全部需求','6','2 条审批中'],
        ['招聘中','2','含紧急岗位'],
        ['待审批','2','下一节点明确'],
        ['已关闭','1','本期完成']
      ],
      'recruit-demand-detail': [
        ['候选人','15','当前岗位池'],
        ['已选择','0','批量操作'],
        ['面试中','5','需跟进评价'],
        ['匹配过期','3','建议重算']
      ],
      'recruit-talent': [
        ['外部候选人','7','可筛选入库'],
        ['内部人才','6','可调岗评估'],
        ['黑名单','2','风险隔离'],
        ['待联系','4','人工确认']
      ],
      'recruit-interview': [
        ['待安排','3','本周优先'],
        ['待评价','5','面试后闭环'],
        ['待入职','2','Offer 跟进'],
        ['已入职','5','本月完成']
      ],
      'recruit-ai': [
        ['摘要任务','12','简历解析'],
        ['话术草稿','8','人工发送'],
        ['效率分析','4','可下钻'],
        ['风险提醒','3','需复核']
      ],
      'recruit-config': [
        ['邮箱账号','2','1 个异常'],
        ['渠道配置','4','全部启用'],
        ['流程节点','3','可维护'],
        ['通知模板','5','最近更新']
      ]
    };
    var items = presets[route];
    if(!items) return;
    var summary = document.createElement('section');
    summary.className = 'hero-page-summary';
    summary.setAttribute('aria-label','页面关键指标');
    summary.innerHTML = items.map(function(item, index){
      return '<article class="hero-summary-card"><span>'+item[0]+'</span><strong>'+item[1]+'</strong><em>'+item[2]+'</em><i style="--i:'+index+'"></i></article>';
    }).join('');
    var tabs = body.querySelector('.hero-module-tabs');
    if(tabs) tabs.insertAdjacentElement('afterend', summary);
    else body.insertBefore(summary, body.firstChild);
  }

  function enhanceCoreComponents(){
    document.body.classList.add('hero-pro-workbench');
    document.body.classList.add('core-components-ready');
    ensureHeroModuleTabs();
    ensureHeroPageSummary();
    enhanceMetricCards();
    enhanceStatusLabels();
    enhanceFilterBars();
    enhanceTables();
    enhanceBatchBars();
    enhanceDialogs();
    enhanceEmptyStates();
    enhanceVisualizationCards();
    ensureHeroDashboardAnalytics();
  }

  var componentTimer = null;
  function scheduleCoreEnhancements(){
    clearTimeout(componentTimer);
    componentTimer = setTimeout(enhanceCoreComponents, 30);
  }

  function closePalette(){
    var palette = document.getElementById('commandPalette');
    if(palette) palette.remove();
  }

  function renderPalette(){
    closePalette();
    var overlay = document.createElement('div');
    overlay.id = 'commandPalette';
    overlay.className = 'command-palette-overlay';
    overlay.innerHTML =
      '<div class="command-palette" role="dialog" aria-modal="true" aria-label="快速跳转">' +
        '<div class="command-input-row"><span>Ctrl K</span><input id="commandInput" type="search" placeholder="搜索页面或操作" autocomplete="off"></div>' +
        '<div class="command-results" id="commandResults"></div>' +
      '</div>';
    overlay.addEventListener('click', function(e){ if(e.target === overlay) closePalette(); });
    document.body.appendChild(overlay);
    var input = document.getElementById('commandInput');
    var results = document.getElementById('commandResults');
    function draw(){
      var q = input.value.trim().toLowerCase();
      var source = allowedCommands();
      var items = source.filter(function(item){
        return !q || item.label.toLowerCase().indexOf(q) !== -1 || item.hint.toLowerCase().indexOf(q) !== -1;
      });
      results.innerHTML = (items.length ? items : source).map(function(item){
        return '<button class="command-result" data-href="'+item.href+'"><strong>'+item.label+'</strong><span>'+item.hint+'</span></button>';
      }).join('');
      Array.prototype.forEach.call(results.querySelectorAll('.command-result'), function(btn){
        btn.onclick = function(){ go(btn.getAttribute('data-href')); closePalette(); };
      });
    }
    input.addEventListener('input', draw);
    input.addEventListener('keydown', function(e){
      if(e.key === 'Enter'){
        var first = results.querySelector('.command-result');
        if(first){ go(first.getAttribute('data-href')); closePalette(); }
      }
      if(e.key === 'Escape') closePalette();
    });
    draw();
    setTimeout(function(){ input.focus(); }, 0);
  }

  function installCommandTrigger(){
    enhanceWorkbenchShell();
    enhanceCoreComponents();
    var actions = ensureTopbarActions();
    if(actions && !document.getElementById('commandTrigger')){
      var btn = document.createElement('button');
      btn.id = 'commandTrigger';
      btn.type = 'button';
      btn.className = 'command-trigger';
      btn.setAttribute('aria-label','打开快速跳转');
      btn.setAttribute('title','快速跳转');
      btn.innerHTML = '<span>快速跳转</span><kbd>Ctrl K</kbd>';
      btn.onclick = renderPalette;
      actions.insertBefore(btn, actions.firstChild);
    }
  }

  document.addEventListener('keydown', function(e){
    if((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k'){
      e.preventDefault();
      renderPalette();
    }
    if(e.key === 'Escape') closePalette();
  });
  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installCommandTrigger);
  else installCommandTrigger();
  setTimeout(installCommandTrigger, 100);
  if(window.MutationObserver){
    new MutationObserver(scheduleCoreEnhancements).observe(document.body, { childList:true, subtree:true });
  }
  window.__enhanceWorkbenchShell = installCommandTrigger;
})();

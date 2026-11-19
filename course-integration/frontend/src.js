import './style.css'
const app=document.querySelector('#app');let token=sessionStorage.getItem('course-token');let identity;
const element=(name,text,parent)=>{const e=document.createElement(name);if(text)e.textContent=text;parent?.append(e);return e};
async function api(path,method='GET',data){const res=await fetch(path,{redirect:'error',method,headers:{...(token?{Authorization:`Bearer ${token}`} : {}),'Content-Type':'application/json','X-Request-ID':crypto.randomUUID()},...(data===undefined?{}:{body:JSON.stringify(data)})});const text=await res.text();let value;try{value=JSON.parse(text)}catch{value=text}if(!res.ok)throw new Error(`${res.status}: ${typeof value==='string'?value:JSON.stringify(value)}`);return value}
function login() {
  app.replaceChildren();
  element('h1', '课程项目管理', app);
  const form = element('form', null, app);
  const role = element('select', null, form);
  role.setAttribute('aria-label', '登录身份');
  for (const [value, label] of [['student','学生'], ['teacher','教师'], ['admin','管理员']]) {
    element('option', label, role).value = value;
  }
  const account = element('input', null, form);
  account.autocomplete = 'username';
  account.required = true;
  account.maxLength = 128;
  const accountLabels = {student:'学号',teacher:'工号',admin:'统一身份用户名'};
  function updateAccountLabel() {
    account.placeholder = accountLabels[role.value];
    account.setAttribute('aria-label', account.placeholder);
  }
  role.onchange = updateAccountLabel;
  updateAccountLabel();
  const password = element('input', null, form);
  password.placeholder = '密码';
  password.setAttribute('aria-label', '密码');
  password.type = 'password';
  password.autocomplete = 'current-password';
  password.required = true;
  password.maxLength = 256;
  const submit = element('button', '登录', form);
  const error = element('pre', '', app);
  error.setAttribute('role', 'alert');
  form.onsubmit = async event => {
    event.preventDefault();
    submit.disabled = true;
    error.textContent = '';
    try {
      const result = role.value === 'admin'
        ? await api('/auth/login', 'POST', {username:account.value,password:password.value,systems:['course-project-management']})
        : await api('/login', 'POST', {account:account.value,password:password.value,role:role.value});
      token = result.accessToken;
      await home(role.value === 'admin' ? 'admin' : undefined);
      sessionStorage.setItem('course-token', token);
    } catch (failure) {
      token = null;
      error.textContent = failure.message;
    } finally {
      password.value = '';
      submit.disabled = false;
    }
  };
}
async function home(requiredRole){identity=await api('/auth/me');if(requiredRole&&!identity.roles?.includes(requiredRole))throw new Error('当前账号没有课程管理员身份。');app.replaceChildren();element('h1','真实课程工作台',app);element('p',`${identity.name||identity.subject} · ${(identity.roles||[]).join(' / ')}`,app);const logout=element('button','退出',app);logout.onclick=async()=>{try{await api('/auth/logout','POST')}finally{token=null;sessionStorage.removeItem('course-token');login()}};const nav=element('nav',null,app);const out=element('pre','',app);const query=(label,path)=>{const b=element('button',label,nav);b.onclick=async()=>{try{out.textContent=JSON.stringify(await api(`/api${path}`),null,2)}catch(e){out.textContent=e.message}}};query('我的身份（含本地映射）','/me');query('学期','/terms');if(identity.roles?.includes('teacher'))query('教师课程','/teacher/courses');if(identity.roles?.includes('student'))query('我的课程','/me/courses');if(identity.roles?.includes('admin'))query('课程审核列表','/admin/courses');
// A real request editor for nontrivial teacher/student operations, not a static data prototype.
element('h2','课程 API 操作',app);element('p','仅调用网关同源 /api。所有权限由中心与真实课程业务双重校验。',app);const f=element('form',null,app),method=element('select',null,f);for(const value of ['GET','POST','PATCH','PUT','DELETE'])element('option',value,method);const path=element('input',null,f);path.value='/terms';path.setAttribute('aria-label','课程API路径');const body=element('textarea',null,f);body.placeholder='JSON 请求正文（GET留空）';const send=element('button','发送真实请求',f);f.onsubmit=async ev=>{ev.preventDefault();if(!path.value.startsWith('/')||path.value.startsWith('//')||path.value.includes('://'))return;send.disabled=true;try{out.textContent=JSON.stringify(await api('/api'+path.value,method.value,body.value?JSON.parse(body.value):undefined),null,2)}catch(e){out.textContent=e.message}finally{send.disabled=false}};
}
if(token)home().catch(()=>{token=null;sessionStorage.removeItem('course-token');login()});else login();

import assert from 'node:assert/strict';

/** 使用真实权限中心、网关和课程服务验证教师鉴权协议和课程登录。 */
export async function verifyIdentityAndLogin({center, edge, accounts, serviceKey, courseDb}) {
  const observations = [];
  const {tokens, subjects, users, department, wrongAudience, coursePassword, localAccounts} = accounts;
  async function request(origin, path, expected, {method='GET', token, body, key}={}) {
    const response = await fetch(origin + path, {
      method,
      redirect:'error',
      signal:AbortSignal.timeout(30000),
      headers:{
        ...(token ? {Authorization:'Bearer '+token} : {}),
        ...(body ? {'Content-Type':'application/json'} : {}),
        ...(key ? {'X-Audit-Service-Key':key} : {}),
      },
      ...(body ? {body:JSON.stringify(body)} : {}),
    });
    assert.equal(response.status, expected, method+' '+path);
    observations.push({method, path, status:response.status});
    if (response.status !== 204 && response.headers.get('content-type')?.includes('json')) {
      return response.json();
    }
    return null;
  }

  const admin = {token:tokens.ADMIN};
  const roots = await request(edge, '/auth/departments', 200, admin);
  assert.deepEqual(Object.keys(roots).sort(), ['items','page','pageSize','total']);
  assert.equal(roots.page, 1);
  assert.equal(roots.pageSize, 20);
  assert(roots.items.length >= 1);
  const root = roots.items.find(item => item.id === department.parentId);
  assert.equal(root.parentId, null);
  assert.equal(root.hasChildren, true);
  assert.deepEqual(Object.keys(root).sort(), ['code','hasChildren','id','name','parentId']);
  const children = await request(edge,
      '/auth/departments?parentId='+root.id+'&keyword=M6_REAL&page=1&pageSize=1', 200, admin);
  assert.equal(children.total, 1);
  assert.equal(children.items[0].id, department.id);
  assert.equal(children.items[0].hasChildren, false);
  const empty = await request(edge, '/auth/departments?page=2147483647&pageSize=100', 200, admin);
  assert.deepEqual(empty.items, []);
  assert.equal(empty.total, roots.total);

  const membersPath = '/auth/departments/'+department.id+'/users';
  const teacher = users.LEADER;
  const members = await request(edge, membersPath+'?keyword='+teacher.no+'&pageSize=1', 200, admin);
  assert.equal(members.total, 1);
  assert.deepEqual(members.items, [{
    userId:subjects.LEADER, name:teacher.name, no:teacher.no,
    departmentId:department.id, departmentName:department.name,
  }]);
  const targetPath = '/auth/users/'+encodeURIComponent(subjects.LEADER);
  assert.deepEqual(await request(edge, targetPath, 200, admin),
      {userId:subjects.LEADER, name:teacher.name, no:teacher.no});
  assert.equal((await request(edge, '/auth/me', 200, admin)).subject, subjects.ADMIN);
  assert.equal((await request(edge, '/auth/api-permissions/listTerms', 200, admin)).allowed, true);
  assert.equal(typeof (await request(edge, '/auth/permissions/TEC', 200, admin)).allowed, 'boolean');

  for (const path of ['/auth/departments', membersPath, targetPath]) {
    await request(edge, path, 401);
    await request(edge, path, 403, {token:wrongAudience});
    await request(edge, path, 403, {token:tokens.ADMIN_NO_TEC});
    await request(edge, path, 403, {token:tokens.LEADER});
  }
  await request(edge, '/auth/departments?page=0', 400, admin);
  await request(edge, membersPath+'?pageSize=101', 400, admin);
  await request(edge, '/auth/departments?parentId=999999', 404, admin);
  await request(edge, '/auth/departments/999999/users', 404, admin);
  await request(edge, '/auth/users/nonexistent-subject', 404, admin);
  await request(edge, '/identity/users/'+subjects.LEADER, 403, admin);
  await request(center, '/identity/users/'+subjects.LEADER, 200, {...admin, key:serviceKey});

  const loginBody = {role:'teacher', account:localAccounts.LEADER, password:coursePassword};
  const loginOptions = {method:'POST', body:loginBody};
  await request(edge, '/login', 404, {...loginOptions,body:{...loginBody,account:'unknown-fixture'}});
  await request(edge, '/login', 401, {...loginOptions,body:{...loginBody,password:'wrong-fixture'}});
  await request(edge, '/login', 401);
  await request(edge, '/api/terms', 401);
  await request(edge, '/api/terms', 200, admin);
  const login = await request(edge, '/login', 200, loginOptions);
  assert.deepEqual(Object.keys(login).sort(), ['accessToken','expiresIn','tokenType','user']);
  assert.equal(login.tokenType, 'Bearer');
  assert(login.accessToken && login.expiresIn > 0);
  assert.equal((await request(edge, '/auth/me', 200, {token:login.accessToken})).subject, subjects.LEADER);
  await request(edge, '/api/me', 200, {token:login.accessToken});
  // 测试首次绑定，学生已存在但尚未绑定统一身份 subject。
  try {
    await courseDb.execute('UPDATE student SET user_id=NULL WHERE id=610001 AND user_id=?', [subjects.STUDENT_LEADER]);
    const student = await request(edge, '/login', 200, {
      method:'POST', body:{role:'student', account:localAccounts.STUDENT_LEADER,password:coursePassword},
    });
    assert.equal((await request(edge, '/auth/me', 200, {token:student.accessToken})).subject, subjects.STUDENT_LEADER);
    const [[binding]] = await courseDb.query('SELECT user_id FROM student WHERE id=610001');
    assert.equal(binding.user_id, subjects.STUDENT_LEADER);
  } finally {
    await courseDb.execute('UPDATE student SET user_id=? WHERE id=610001', [subjects.STUDENT_LEADER]);
  }

  await request(edge, '/auth/logout', 204, {method:'POST', token:login.accessToken});
  await request(edge, '/auth/me', 401, {token:login.accessToken});
  await request(edge, '/auth/audit-logs', 403, {...admin,key:serviceKey});
  const audit = await request(center, '/auth/audit-logs?pageSize=1', 200, {...admin,key:serviceKey});
  assert.deepEqual(Object.keys(audit).sort(), ['items','page','pageSize','total']);
  return observations;
}

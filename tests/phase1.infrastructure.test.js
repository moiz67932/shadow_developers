const {spawnSync}=require('child_process');
const root=require('path').join(__dirname,'..');
test('Compose resolves all required infrastructure, ports, browser resources and salt forwarding',()=>{
  const result=spawnSync('docker',['compose','config','--format','json'],{cwd:root,env:{...process.env,DAY1_SALT:'configuration-test-only',DAY2_SALT:'configuration-test-only'},encoding:'utf8'});
  expect(result.status).toBe(0);const {services}=JSON.parse(result.stdout);
  for(const name of ['db','redis','api','worker','minio','mediamtx','selenium-hub','node-chrome-1','node-chrome-2','node-firefox'])expect(services[name].healthcheck).toBeDefined();
  expect(services.api.environment.DAY2_SALT).toBe('configuration-test-only');expect(services.worker.environment.DAY2_SALT).toBe('configuration-test-only');
  expect(services.api.ports.some(p=>p.published==='8080')).toBe(true);
  for(const port of ['1935','8888','8889'])expect(services.mediamtx.ports.some(p=>p.published===port)).toBe(true);
  expect(services['selenium-hub'].ports.some(p=>p.published==='4444')).toBe(true);
  for(const name of ['node-chrome-1','node-chrome-2','node-firefox']){
    const node=services[name];expect(node.image).toBe('selenium/'+(name==='node-firefox'?'node-firefox':'node-chrome')+':4.25.0');
    expect(node.shm_size).toBe('1073741824');expect(node.environment.SE_START_VNC).toBe('false');expect(node.environment.SE_START_NO_VNC).toBe('false');expect(node.environment.SE_NODE_MAX_SESSIONS).toBe('2');
  }
  expect(services.minio.profiles).toBeUndefined();
});

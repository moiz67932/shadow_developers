const crypto=require('crypto');
const integration=process.env.MINIO_URL?describe:describe.skip;
integration('Actual private MinIO object storage helper',()=>{
test('SigV4 upload/read/delete preserves recording bytes and leaves no test object',async()=>{const storage=require('../backend/src/services/object-storage'),key='qa/'+crypto.randomUUID()+'.mp4',data=Buffer.from('NASCON recording bytes · پاکستان');try{await storage.put(key,data);const response=await storage.get(key);expect(response.status).toBe(200);expect(Buffer.from(await response.arrayBuffer())).toEqual(data);}finally{await storage.remove(key);}expect((await storage.get(key)).status).toBe(404);});});

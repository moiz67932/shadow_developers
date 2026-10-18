const express=require('express');
const router=express.Router();
const streaming=require('../domain/streaming'),costream=require('../domain/costream'),qr=require('../domain/qr_guest'),moderation=require('../domain/moderation_dlq'),observability=require('../domain/observability');
const wrap=fn=>(req,res,next)=>Promise.resolve().then(()=>fn(req,res)).catch(next);
router.post('/audit/v1/stream/key',wrap(async(req,res)=>{const r=await streaming.key(req.body);await require('../services/media-control').invalidate(r.invalidated);res.status(r.status).json(r.body);}));
router.post('/internal/media/auth',wrap(async(req,res)=>{
  const {z,parse}=require('../utils/validation'),{fail}=require('../utils/errors');
  const b=parse(z.object({action:z.string(),path:z.string().max(512)}),req.body);
  if(req.body.user==='internal'&&req.body.password===require('../services/media-sessions').internalToken()){res.json({authorized:true});return;}
  if(b.path.startsWith('live/'))await streaming.publishAllowed(b.path);
  else if(b.action==='publish')fail(403,'FORBIDDEN','Only active stream keys may publish.');
  if(!['publish','read','playback'].includes(b.action))fail(403,'FORBIDDEN','Unsupported media action.');res.json({authorized:true});
}));
router.post('/audit/v1/costream/session',wrap(async(req,res)=>{const r=await costream.session(req.body);res.status(r.status).json(r.body);}));
router.get('/audit/v1/qr/:id/export.:format',wrap(async(req,res)=>{const r=await qr.exportQR(req.params.id,req.params.format);res.type(r.type).send(r.data);}));
router.get('/audit/v1/qr/:id',wrap(async(req,res)=>{const cache=require('../services/qr-cache');if(req.query.inspect==='true')return res.json(await cache.inspect(req.params.id,()=>qr.resolve(req.params.id,true,req.headers['user-agent']??'')));cache.invalidate();res.json(await qr.resolve(req.params.id,false,req.headers['user-agent']??''));}));
router.get('/q/:code',wrap(async(req,res)=>{const meta=await qr.redirect(req.params.code,req.headers['user-agent']??'');res.set('Cache-Control','no-store').redirect(302,meta.target_route);}));
router.post('/audit/v1/guest/cart-merge',wrap(async(req,res)=>res.json(await qr.merge(req.body,req.headers.authorization?require('../domain/rbac').authenticateSession(req.headers.authorization):undefined))));
router.post('/audit/v1/moderation/test',wrap(async(req,res)=>res.json(await moderation.moderate(req.body))));
router.get('/metrics',wrap(async(req,res)=>res.set('Content-Type','text/plain; version=0.0.4').end(await observability.metrics())));
router.delete('/audit/v1/privacy/user/:id',wrap(async(req,res)=>res.json(await observability.erase(req.params.id))));
module.exports=router;

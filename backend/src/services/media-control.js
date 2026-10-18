async function invalidate(keys){if(!keys.length||!process.env.MEDIA_API_URL)return;
  const base=process.env.MEDIA_API_URL;
  await Promise.all(['rtmpconns','webrtcsessions'].map(async type=>{
    const r=await fetch(`${base}/v3/${type}/list`,{signal:AbortSignal.timeout(600)});if(!r.ok)throw new Error('Media control API unavailable.');
    const sessions=(await r.json()).items??[];
    await Promise.all(sessions.filter(s=>keys.some(k=>s.path==='live/'+k)).map(s=>fetch(`${base}/v3/${type}/kick/${encodeURIComponent(s.id)}`,{method:'POST',signal:AbortSignal.timeout(600)}).then(r=>{if(!r.ok&&r.status!==404)throw new Error('Media publisher invalidation failed.');})));
  }));
}
module.exports={invalidate};

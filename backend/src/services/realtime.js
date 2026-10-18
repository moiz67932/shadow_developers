const {WebSocketServer,WebSocket}=require('ws');
const connections=new Set(),subscribers=new Map();
function payload(view){return {type:'score_update',...view,score:view,timestamp_ms:Date.now()};}
function publish(view){const message=JSON.stringify(payload(view));for(const client of connections)if(client.match_id===view.match_id&&client.readyState===WebSocket.OPEN)client.send(message);for(const callback of subscribers.get(view.match_id)??[])callback(message);}
function subscribe(match_id,callback){if(!subscribers.has(match_id))subscribers.set(match_id,new Set());subscribers.get(match_id).add(callback);return ()=>{const listeners=subscribers.get(match_id);listeners?.delete(callback);if(!listeners?.size)subscribers.delete(match_id);};}
function attach(server){
  const wss=new WebSocketServer({noServer:true,maxPayload:1024*1024});
  server.on('upgrade',async(req,socket,head)=>{
    const match=/^\/ws\/matches\/([A-Za-z0-9_.:-]{1,64})$/.exec(new URL(req.url,'http://localhost').pathname);
    if(!match){socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');return;}
    try{const state=await require('./state').getState(),view=require('../domain/scoring').project(state,match[1]);
      wss.handleUpgrade(req,socket,head,client=>{client.match_id=match[1];connections.add(client);client.on('error',()=>{});client.on('close',()=>connections.delete(client));client.on('message',()=>client.close(1008,'Read-only score subscription'));client.send(JSON.stringify(payload(view)));});
    }catch{socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');}
  });
  server.on('close',()=>{for(const client of connections)client.terminate();wss.close();});
  return wss;
}
module.exports={connections,connectionCount:()=>connections.size,publish,subscribe,attach};

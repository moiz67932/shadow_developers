// Transport attachment is Phase 3; the metrics source already reflects real state.
const connections=new Set();
module.exports={connections,connectionCount:()=>connections.size};

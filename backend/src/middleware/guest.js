function guestWriteGuard(req,res,next){
  const mutation=!['GET','HEAD','OPTIONS'].includes(req.method);
  if(mutation && req.path!=='/api/v1/login' && /^Bearer\s+guest_/i.test(req.headers.authorization??'')) {
    return next(Object.assign(new Error('Anonymous guest tokens cannot perform write mutations.'),{
      status:403,code:'GUEST_WRITE_FORBIDDEN',details:{}
    }));
  }
  next();
}
module.exports={guestWriteGuard};

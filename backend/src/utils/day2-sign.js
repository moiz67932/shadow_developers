const config=require('../config');
const {sign}=require('./canonical');
const {fail}=require('./errors');
function signDay2(id,payload){if(!config.day2Salt)fail(503,'NOT_READY','DAY2_SALT is not configured.');return sign(id,payload,config.day2Salt);}
module.exports={signDay2};

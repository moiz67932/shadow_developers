"""Verify actual WHEP-decoded frames through MediaMTX's bundled player."""
import json
import sys
import time
sys.path.insert(0,'tests/selenium')
from test_grid_infrastructure import call
sid=call('/session',{'capabilities':{'alwaysMatch':{'browserName':'chrome','goog:chromeOptions':{'args':['--headless=new','--no-sandbox','--autoplay-policy=no-user-gesture-required']}}}})['sessionId']
try:
    call('/session/'+sid+'/url',{'url':'http://mediamtx:8889/'+sys.argv[1]+'?muted=true&autoplay=true'})
    deadline=time.monotonic()+8
    result={}
    while time.monotonic()<deadline:
        result=call('/session/'+sid+'/execute/sync',{'script':'const v=document.querySelector("video");return {decoded:!!v&&v.videoWidth>0&&v.currentTime>0,width:v?.videoWidth||0,time:v?.currentTime||0};','args':[]})
        if result.get('decoded'):break
        time.sleep(.2)
    print(json.dumps(result))
finally:call('/session/'+sid,method='DELETE')

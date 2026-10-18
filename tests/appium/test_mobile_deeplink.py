"""Prepared Appium 2 source-level journeys. Run only with a matching installed APK."""
import json
import os
import unittest
import urllib.request
SERVER=os.environ.get('APPIUM_URL','http://localhost:4723')
def call(path,payload=None,method=None):
    req=urllib.request.Request(SERVER+path,data=json.dumps(payload).encode() if payload is not None else None,headers={'Content-Type':'application/json'},method=method)
    with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)['value']
@unittest.skipUnless(os.environ.get('MATCHING_APK_INSTALLED')=='1','Matching Day 2 APK/device not verified; no build is performed')
class MobileDeepLinks(unittest.TestCase):
    def test_match_and_stream_external_intents(self):
        caps={'platformName':'Android','appium:automationName':'UiAutomator2','appium:deviceName':os.environ.get('ANDROID_DEVICE','Infinix'),'appium:noReset':True,'appium:appPackage':'com.example.nasconsports','appium:appActivity':'.MainActivity'}
        sid=call('/session',{'capabilities':{'alwaysMatch':caps}})['sessionId']
        try:
            for uri in ['nascon://entity/match-m1','nascon://stream/cs-session-101']:
                call('/session/'+sid+'/execute/sync',{'script':'mobile: deepLink','args':[{'url':uri,'package':'com.example.nasconsports'}]})
                activity=call('/session/'+sid+'/appium/device/current_activity')
                self.assertIn('Day2LiveActivity',activity)
        finally:call('/session/'+sid,method='DELETE')
if __name__=='__main__':unittest.main()

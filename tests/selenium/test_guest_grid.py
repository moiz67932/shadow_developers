"""Real Chrome/Chrome/Firefox journeys; stdlib WebDriver transport, pytest compatible.
Run against an isolated QA API: GUEST_BASE_URL=http://172.18.0.1:18080
GUEST_CONTROL_URL=http://localhost:18080 python3 -m unittest discover -s tests/selenium -p test_guest_grid.py
"""
import concurrent.futures
import json
import os
import time
import unittest
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path
from test_grid_infrastructure import call

BASE = os.environ.get('GUEST_BASE_URL', 'http://api:8080')
CONTROL = os.environ.get('GUEST_CONTROL_URL', 'http://localhost:8080')
def api(path, body=None):
    req=urllib.request.Request(CONTROL+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=5) as response:return json.load(response)

def journey(browser, scenario):
    caps={'browserName':browser}
    caps['goog:chromeOptions' if browser=='chrome' else 'moz:firefoxOptions']={'args':['--headless=new','--no-sandbox'] if browser=='chrome' else ['-headless']}
    sid=call('/session',{'capabilities':{'alwaysMatch':caps}})['sessionId']
    def js(code):return call(f'/session/{sid}/execute/sync',{'script':code,'args':[]})
    def wait(code,timeout=3):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if js(code):return
            time.sleep(.025)
        raise AssertionError('Browser assertion timed out: '+code)
    try:
        call(f'/session/{sid}/url',{'url':BASE+'/q/m1-live'})
        wait('return document.querySelector("[data-testid=live-score-banner]")?.textContent.includes(" - ")')
        assert '/web/matches/match-m1/live' in js('return location.pathname')
        assert js('return document.getElementById("auth").hidden')
        assert js('return document.querySelector("[data-testid=guest-video-player]").getBoundingClientRect().height>0')
        for testid in ['pinned-merch-card','add-to-guest-cart-btn','guest-cart-count','chat-message-list','chat-input-field','chat-send-btn','progressive-auth-modal']:
            assert js('return !!document.querySelector(\'[data-testid="'+testid+'"]\')')
        if scenario=='write':
            before=js('return document.getElementById("messages").children.length')
            js('document.getElementById("chat").value="Guest write probe";document.getElementById("send").click();return true')
            wait('return !document.getElementById("auth").hidden')
            assert js('return document.getElementById("messages").children.length')==before
        if scenario=='score':
            js('document.getElementById("add").click();return true')
            wait('return document.getElementById("count").textContent==="1"')
            old=api('/web/api/context/match-m1')['match'];expected=str(old['home']['score']+1)+' - '+str(old['away']['score'])
            event_id='grid-'+uuid.uuid4().hex;started=time.monotonic()
            api('/audit/v1/events',{'event_id':event_id,'match_id':'match-m1','type':'score','payload':{'team_id':'club-alpha-1','event_type':'goal'}})
            try:
                wait('return document.querySelector("[data-testid=live-score-banner]").textContent==='+json.dumps(expected),.5)
                assert time.monotonic()-started<.5
            finally:api('/audit/v1/events',{'event_id':event_id+'-undo','match_id':'match-m1','type':'compensation','payload':{'compensates':event_id,'reason':'Undo isolated browser QA score'}})
        return {'browser':browser,'scenario':scenario,'passed':True}
    finally:call(f'/session/{sid}',method='DELETE')

class GuestGrid(unittest.TestCase):
    def test_three_parallel_browser_journeys(self):
        jobs=[('chrome','landing'),('chrome','write'),('firefox','score')]
        results=[];suite=ET.Element('testsuite',name='Guest Grid',tests='3',failures='0')
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            futures=[executor.submit(journey,*job) for job in jobs]
            for job,future in zip(jobs,futures):
                case=ET.SubElement(suite,'testcase',classname=job[0],name=job[1])
                try:results.append(future.result())
                except Exception as error:ET.SubElement(case,'failure',message=str(error));suite.set('failures',str(int(suite.get('failures'))+1));results.append({'browser':job[0],'scenario':job[1],'passed':False,'error':str(error)})
        Path('tests/reports').mkdir(exist_ok=True)
        ET.ElementTree(suite).write('tests/reports/selenium-report.xml',encoding='utf-8',xml_declaration=True)
        Path('tests/reports/selenium-results.json').write_text(json.dumps(results,indent=2)+'\n')
        self.assertTrue(all(r['passed'] for r in results),results)

if __name__=='__main__':unittest.main()

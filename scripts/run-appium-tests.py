"""Emit genuine unittest pass/fail/skip evidence; never install or build Android."""
import unittest
import xml.etree.ElementTree as ET
result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover('tests/appium'))
suite=ET.Element('testsuite',name='Appium deep links',tests=str(result.testsRun),failures=str(len(result.failures)),errors=str(len(result.errors)),skipped=str(len(result.skipped)))
for test,reason in result.skipped:
    ET.SubElement(ET.SubElement(suite,'testcase',name=str(test)),'skipped',message=reason)
for test,message in result.failures+result.errors:
    ET.SubElement(ET.SubElement(suite,'testcase',name=str(test)),'failure').text=message
ET.ElementTree(suite).write('tests/reports/appium-report.xml',encoding='utf-8',xml_declaration=True)
raise SystemExit(0 if result.wasSuccessful() else 1)

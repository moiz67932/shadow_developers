Run an existing Appium 2 installation with its UiAutomator2 driver on port 4723.
No Appium server/driver installation or matching Day 2 APK was verified here.
After another session builds and installs the current source:

`MATCHING_APK_INSTALLED=1 python3 -m unittest discover -s tests/appium`

Both required custom-scheme intents target the isolated `Day2LiveActivity` and
do not require a signed-in account. Compilation, device routing, native playback,
notifications and cross-surface co-stream chat remain unverified.

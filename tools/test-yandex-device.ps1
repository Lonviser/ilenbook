param([string]$JdkPath='D:\Soft\PyCharm 2026.1\jbr')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot
$taskSdk=Join-Path $taskRoot '.tools\android-sdk'
$taskBt=Join-Path $taskSdk 'build-tools\35.0.0'
$taskWork=Join-Path $taskRoot 'build\yandex-device-tests'
New-Item -ItemType Directory -Force -Path $taskWork,"$taskWork\classes","$taskWork\dex" | Out-Null
@'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="by.list.reader.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="ilenbook tests"/><instrumentation android:name="by.list.reader.YandexDeviceChecks" android:targetPackage="by.list.reader" /></manifest>
'@ | Set-Content -LiteralPath "$taskWork\AndroidManifest.xml" -Encoding utf8
function Run-CheckTool([string]$File,[string[]]$Arguments){& $File @Arguments;if($LASTEXITCODE){throw "$File failed"}}
Run-CheckTool "$JdkPath\bin\javac.exe" @('-encoding','UTF-8','--release','8','-classpath',"$taskSdk\platforms\android-35\android.jar;$taskRoot\build\classes",'-d',"$taskWork\classes","$PSScriptRoot\YandexDeviceChecks.java")
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path -LiteralPath "$taskWork\classes.jar"){[IO.File]::Delete("$taskWork\classes.jar")}
[IO.Compression.ZipFile]::CreateFromDirectory("$taskWork\classes","$taskWork\classes.jar")
Run-CheckTool "$JdkPath\bin\java.exe" @('-cp',"$taskBt\lib\d8.jar",'com.android.tools.r8.D8','--lib',"$taskSdk\platforms\android-35\android.jar",'--min-api','26','--output',"$taskWork\dex","$taskWork\classes.jar")
Run-CheckTool "$taskBt\aapt2.exe" @('link','-o',"$taskWork\unsigned.apk",'-I',"$taskSdk\platforms\android-35\android.jar",'--manifest',"$taskWork\AndroidManifest.xml")
$taskArchive=[IO.Compression.ZipFile]::Open("$taskWork\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,"$taskWork\dex\classes.dex",'classes.dex')|Out-Null}finally{$taskArchive.Dispose()}
Run-CheckTool "$taskBt\zipalign.exe" @('-f','4',"$taskWork\unsigned.apk","$taskWork\aligned.apk")
Run-CheckTool "$JdkPath\bin\java.exe" @('-jar',"$taskBt\lib\apksigner.jar",'sign','--ks',"$taskRoot\.tools\list-dev.keystore",'--ks-pass','pass:android','--ks-key-alias','list-dev','--key-pass','pass:android','--out',"$taskWork\tests.apk","$taskWork\aligned.apk")
Run-CheckTool "$taskSdk\platform-tools\adb.exe" @('install','-r',"$taskWork\tests.apk")
$taskResult=& "$taskSdk\platform-tools\adb.exe" shell am instrument -w by.list.reader.tests/by.list.reader.YandexDeviceChecks
$taskResult | Tee-Object -FilePath "$taskRoot\output\yandex-device-checks.txt"
if($LASTEXITCODE -or -not($taskResult -match 'native Yandex checks passed')){throw 'Native Yandex checks failed'}

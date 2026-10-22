param([string]$SdkPath = "$PSScriptRoot\.tools\android-sdk", [string]$JdkPath = $env:JAVA_HOME, [switch]$DebugWebView)
$ErrorActionPreference = 'Stop'
if (-not $JdkPath) { $JdkPath = 'D:\Soft\PyCharm 2026.1\jbr' }
$env:JAVA_HOME = $JdkPath
$env:PATH = "$JdkPath\bin;$env:PATH"
$bt = Join-Path $SdkPath 'build-tools\35.0.0'
$androidJar = Join-Path $SdkPath 'platforms\android-35\android.jar'
if (-not (Test-Path -LiteralPath $androidJar)) { throw 'Install Android platform 35 and build-tools 35.0.0; pass -SdkPath.' }
$work = Join-Path $PSScriptRoot 'build'
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force -Path $work, "$work\classes", "$work\dex", $dist | Out-Null
function Run-Tool([string]$file, [string[]]$arguments) { & $file @arguments; if ($LASTEXITCODE -ne 0) { throw "$file failed: $LASTEXITCODE" } }
Run-Tool "$bt\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$work\resources.zip")
[xml]$manifest = Get-Content -LiteralPath "$PSScriptRoot\app\src\main\AndroidManifest.xml" -Raw
$manifest.manifest.application.SetAttribute('debuggable', 'http://schemas.android.com/apk/res/android', $DebugWebView.IsPresent.ToString().ToLowerInvariant())
$manifest.Save("$work\AndroidManifest.xml")
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$apkName="ilenbook-$version$(if($DebugWebView){'-debug'}).apk"
Run-Tool "$bt\aapt2.exe" @('link','-o',"$work\base.apk",'-I',$androidJar,'--manifest',"$work\AndroidManifest.xml",'-A',"$PSScriptRoot\app\src\main\assets",'--auto-add-overlay',"$work\resources.zip")
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\app\src\main\java" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
Run-Tool "$JdkPath\bin\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d',"$work\classes") + $sources)
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path -LiteralPath "$work\classes.jar") { [System.IO.File]::Delete("$work\classes.jar") }
[System.IO.Compression.ZipFile]::CreateFromDirectory("$work\classes", "$work\classes.jar")
Run-Tool "$JdkPath\bin\java.exe" @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$androidJar,'--min-api','26','--output',"$work\dex","$work\classes.jar")
Copy-Item -LiteralPath "$work\base.apk" -Destination "$work\unsigned.apk" -Force
$zip = [System.IO.Compression.ZipFile]::Open("$work\unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$work\dex\classes.dex", 'classes.dex') | Out-Null
    # Windows AAPT2 may write nested asset names with backslashes. AssetManager needs '/'.
    foreach ($entry in @($zip.Entries)) {
        if ($entry.FullName.Contains('\')) {
            $fixed=$zip.CreateEntry($entry.FullName.Replace('\','/'))
            $source=$entry.Open(); $target=$fixed.Open()
            try { $source.CopyTo($target) } finally { $source.Dispose(); $target.Dispose() }
            $entry.Delete()
        }
    }
} finally { $zip.Dispose() }
Run-Tool "$bt\zipalign.exe" @('-f','4',"$work\unsigned.apk","$work\aligned.apk")
$key = Join-Path $PSScriptRoot '.tools\list-dev.keystore'
if (-not (Test-Path -LiteralPath $key)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $key) | Out-Null
    Run-Tool "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-alias','list-dev','-keypass','android','-dname','CN=List Development, O=List Reader, C=BY','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Run-Tool "$JdkPath\bin\java.exe" @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--ks-key-alias','list-dev','--key-pass','pass:android','--out',"$dist\$apkName","$work\aligned.apk")
Run-Tool "$JdkPath\bin\java.exe" @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',"$dist\$apkName")
Get-FileHash -LiteralPath "$dist\$apkName" -Algorithm SHA256 | Format-List

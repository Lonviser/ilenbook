param([string]$Version='0.7.1')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot
$taskDist=Join-Path $taskRoot 'dist'
$taskZip=Join-Path $taskDist "ilenbook-$Version-source.zip"
$taskApk=Join-Path $taskDist "ilenbook-$Version.apk"
if(-not(Test-Path -LiteralPath $taskApk)){throw 'Build the APK first'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
if(Test-Path -LiteralPath $taskZip){[System.IO.File]::Delete($taskZip)}
$taskArchive=[System.IO.Compression.ZipFile]::Open($taskZip,[System.IO.Compression.ZipArchiveMode]::Create)
try{
 $taskFiles=@('app','tools','docs','.github') | ForEach-Object {Get-ChildItem -LiteralPath (Join-Path $taskRoot $_) -Recurse -File}
 $taskFiles+=@('README.md','CONTRIBUTING.md','THIRD_PARTY_NOTICES.md','build-apk.ps1','build.gradle','settings.gradle','.gitignore','.gitattributes') | ForEach-Object {Get-Item -LiteralPath (Join-Path $taskRoot $_)}
 foreach($taskFile in $taskFiles){
  $taskRelative=$taskFile.FullName.Substring($taskRoot.Length+1).Replace('\','/')
  if($taskRelative -match '(^|/)(node_modules|build|output|\.tools|\.git)(/|$)|\.(keystore|apk|idsig)$'){throw "Unexpected source file: $taskRelative"}
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskFile.FullName,$taskRelative,[System.IO.Compression.CompressionLevel]::Optimal)|Out-Null
 }
}finally{$taskArchive.Dispose()}
$taskHashes=@($taskApk,$taskZip) | ForEach-Object {$taskHash=Get-FileHash -LiteralPath $_ -Algorithm SHA256; $taskHash.Hash.ToLowerInvariant()+'  '+[System.IO.Path]::GetFileName($_)}
[System.IO.File]::WriteAllLines((Join-Path $taskDist "ilenbook-$Version-SHA256.txt"),$taskHashes,[System.Text.UTF8Encoding]::new($false))
Get-Item -LiteralPath $taskApk,$taskZip | Select-Object Name,Length

param([string]$Destination = "$PSScriptRoot\..\build\network-libs")
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$networkCache=Join-Path $PSScriptRoot '..\.tools\network'
New-Item -ItemType Directory -Force -Path $networkCache,$Destination | Out-Null
$networkArtifacts=Get-Content -LiteralPath "$PSScriptRoot\network-dependencies.json" -Raw | ConvertFrom-Json
foreach($networkArtifact in $networkArtifacts){
 $networkName=$networkArtifact.Name+'-'+$networkArtifact.Version+'.jar'
 $networkFile=Join-Path $networkCache $networkName
 if(-not(Test-Path -LiteralPath $networkFile)){
  & curl.exe --silent --show-error --fail --location --max-time 120 ('https://repo.maven.apache.org/maven2/org/briarproject/'+$networkArtifact.Name+'/'+$networkArtifact.Version+'/'+$networkName) -o "$networkFile.tmp"
  if($LASTEXITCODE){throw "Cannot download $networkName"}
  Move-Item -LiteralPath "$networkFile.tmp" -Destination $networkFile -Force
 }
 if((Get-FileHash -LiteralPath $networkFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $networkArtifact.sha256){throw "Checksum mismatch: $networkName"}
 $networkZip=[System.IO.Compression.ZipFile]::OpenRead($networkFile)
 try{foreach($networkEntry in $networkZip.Entries){
  if($networkEntry.FullName -notmatch '^(arm64-v8a|armeabi-v7a|x86|x86_64)/lib(tor|lyrebird)\.so$'){throw "Unexpected native entry: $($networkEntry.FullName)"}
  $networkTarget=Join-Path $Destination $networkEntry.FullName
  New-Item -ItemType Directory -Force -Path (Split-Path $networkTarget) | Out-Null
  [System.IO.Compression.ZipFileExtensions]::ExtractToFile($networkEntry,$networkTarget,$true)
 }}finally{$networkZip.Dispose()}
}

param([string]$JdkPath = 'D:\Soft\PyCharm 2026.1\jbr')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$jsonJar=Join-Path $root '.tools\json-20240303.jar'
New-Item -ItemType Directory -Force -Path (Split-Path $jsonJar) | Out-Null
if (-not (Test-Path -LiteralPath $jsonJar)) { & curl.exe -L --fail 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -o $jsonJar; if($LASTEXITCODE){throw 'Cannot download JSON test runtime'} }
$classes=Join-Path $root 'build\parser-checks'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$JdkPath\bin\javac.exe" --release 8 -encoding UTF-8 -classpath $jsonJar -d $classes "$root\app\src\main\java\by\list\reader\BookParser.java" "$root\app\src\main\java\by\list\reader\RichText.java" "$root\app\src\main\java\by\list\reader\LibraryArchive.java" "$root\app\src\main\java\by\list\reader\YandexProtocol.java" "$PSScriptRoot\ParserChecks.java" "$PSScriptRoot\ArchiveChecks.java" "$PSScriptRoot\YandexChecks.java" "$root\app\src\main\java\by\list\reader\FirstSuccessfulFetch.java" "$PSScriptRoot\FastFetchChecks.java" "$root\app\src\main\java\by\list\reader\PublicCatalogs.java" "$PSScriptRoot\PublicCatalogChecks.java"
if($LASTEXITCODE){throw 'Parser test compilation failed'}
& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.ParserChecks
if($LASTEXITCODE){throw 'Parser checks failed'}
& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.ArchiveChecks
if($LASTEXITCODE){throw 'Archive checks failed'}
& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.YandexChecks
if($LASTEXITCODE){throw 'Yandex protocol checks failed'}

& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.FastFetchChecks
if($LASTEXITCODE){throw 'Fast fetch checks failed'}

& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.PublicCatalogChecks
if($LASTEXITCODE){throw 'Public catalog checks failed'}
& "$JdkPath\bin\javac.exe" --release 8 -encoding UTF-8 -d $classes "$root\app\src\main\java\by\list\reader\FlibustaAddress.java" "$PSScriptRoot\FlibustaChecks.java"
if($LASTEXITCODE){throw 'Flibusta test compilation failed'}
& "$JdkPath\bin\java.exe" -cp $classes by.list.reader.FlibustaChecks
if($LASTEXITCODE){throw 'Flibusta routing checks failed'}
& "$JdkPath\bin\javac.exe" --release 8 -encoding UTF-8 -classpath $jsonJar -d $classes "$root\app\src\main\java\by\list\reader\UpdatePolicy.java" "$PSScriptRoot\UpdateChecks.java"
if($LASTEXITCODE){throw 'Update check compilation failed'}
& "$JdkPath\bin\java.exe" -cp "$classes;$jsonJar" by.list.reader.UpdateChecks
if($LASTEXITCODE){throw 'Update checks failed'}

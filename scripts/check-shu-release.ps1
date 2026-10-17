# Read-only release check. Does not download/install an app, use accounts or delete files.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$releaseEndpoint = 'https://apps-data.cfw.sh/utility/builds'
$releaseCatalog = Invoke-RestMethod -Uri $releaseEndpoint -TimeoutSec 20
if (-not $releaseCatalog.platforms.android.artifacts.apk.version_string) {
    throw 'Official release response lacks an Android APK version.'
}

$androidRelease = $releaseCatalog.platforms.android.artifacts.apk
$releaseUrl = [Uri]::new([Uri]'https://apps-data.cfw.sh', [string]$androidRelease.download_url)
if ($releaseUrl.Scheme -ne 'https' -or $releaseUrl.Host -ne 'apps-data.cfw.sh') {
    throw 'Unexpected release host. Review manually before downloading.'
}

[pscustomobject]@{
    CheckedAtUtc = [DateTime]::UtcNow.ToString('o')
    Source = $releaseEndpoint
    Channel = 'release'
    AndroidVersion = $androidRelease.version_string
    AndroidBuild = $androidRelease.version_code
    AndroidBytes = $androidRelease.size
    AndroidDownload = $releaseUrl.AbsoluteUri
    WindowsVersion = $releaseCatalog.platforms.win.version_string
    WindowsBuild = $releaseCatalog.platforms.win.version_code
}

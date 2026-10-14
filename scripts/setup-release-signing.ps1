# Set up a private signing key for Split-Android. Run on your own Windows PC.
# Nothing secret is committed to Git; gh encrypts secrets before uploading them.
[CmdletBinding()]
param(
    [string]$Repository = 'farhan2166-creator/Split-Android',
    [string]$KeystorePath = '',
    [string]$KeyAlias = 'split-release',
    [switch]$PublishRelease,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw 'Repository must use owner/repository format.'
}
if ($KeyAlias -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'Invalid key alias.'
}
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    throw 'Install GitHub CLI from https://cli.github.com/ and rerun.'
}
$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) {
    throw 'Install Java JDK 17+ (keytool) and rerun.'
}

Write-Host 'Checking GitHub authentication...'
& gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Authenticate to GitHub in your browser.'
    & gh auth login --hostname github.com --web
    if ($LASTEXITCODE -ne 0) { throw 'GitHub authentication failed.' }
}
& gh repo view $Repository --json nameWithOwner --jq '.nameWithOwner'
if ($LASTEXITCODE -ne 0) { throw "No access to repository: $Repository" }

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
    $KeystorePath = Join-Path $HOME 'Split-Android-Signing\split-release.jks'
}
$KeystorePath = [System.IO.Path]::GetFullPath($KeystorePath)
$created = $false

if (-not [System.IO.File]::Exists($KeystorePath)) {
    Write-Warning 'No existing signing keystore found at the selected path.'
    Write-Warning 'Creating a NEW signing key will not update an APK signed by a different release key.'
    $approval = Read-Host 'Type CREATE to make a new Split-Android release signing key'
    if ($approval -cne 'CREATE') { throw 'Cancelled without modifying signing keys.' }
    $directory = Split-Path -Parent $KeystorePath
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    Write-Host 'Keytool will ask for a NEW keystore password. Save it in your password manager.'
    Write-Host 'At the private key password prompt, press Enter to reuse that same password.'
    & keytool -genkeypair -keystore $KeystorePath -storetype JKS -alias $KeyAlias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Split Android, OU=Mobile, O=Split, C=PK'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $KeystorePath)) {
        throw 'Keytool did not generate the release signing key.'
    }
    $created = $true
} else {
    Write-Host "Reusing signing keystore: $KeystorePath"
    Write-Warning 'Keep the same keystore and key alias for every future signed release.'
}

function Convert-SecureToText([System.Security.SecureString]$Secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

# Passing via redirected stdin avoids leaking values into process command lines.
function Set-ActionsSecret([string]$Name, [string]$Value) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = (Get-Command gh).Source
    $start.Arguments = "secret set $Name --repo $Repository --app actions"
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $start
    try {
        if (-not $proc.Start()) { throw "Could not run gh for $Name" }
        $proc.StandardInput.Write($Value)
        $proc.StandardInput.Close()
        $stderr = $proc.StandardError.ReadToEnd()
        $stdout = $proc.StandardOutput.ReadToEnd()
        $proc.WaitForExit()
        if ($proc.ExitCode -ne 0) {
            throw "GitHub rejected $Name. $stderr $stdout"
        }
        Write-Host "Configured $Name"
    } finally { $proc.Dispose() }
}

$storeSecure = Read-Host 'Enter release keystore password' -AsSecureString
$storePassword = Convert-SecureToText $storeSecure
if ([string]::IsNullOrEmpty($storePassword)) { throw 'Keystore password cannot be blank.' }
if ($storePassword.Contains([char]10) -or $storePassword.Contains([char]13)) {
    throw 'Passwords cannot contain line breaks.'
}

if ($created) {
    $keyPassword = $storePassword
} else {
    $keySecure = Read-Host 'Enter existing PRIVATE KEY password (Enter if same)' -AsSecureString
    $keyPassword = Convert-SecureToText $keySecure
    if ([string]::IsNullOrEmpty($keyPassword)) {
        Write-Host 'Using the keystore password for the private key.'
        $keyPassword = $storePassword
    }
}
if ($keyPassword.Contains([char]10) -or $keyPassword.Contains([char]13)) {
    throw 'Passwords cannot contain line breaks.'
}

$encoded = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($KeystorePath))
try {
    Set-ActionsSecret 'ANDROID_KEYSTORE_BASE64' $encoded
    Set-ActionsSecret 'ANDROID_KEYSTORE_PASSWORD' $storePassword
    Set-ActionsSecret 'ANDROID_KEY_ALIAS' $KeyAlias
    Set-ActionsSecret 'ANDROID_KEY_PASSWORD' $keyPassword
} finally {
    $encoded = $null
    $storePassword = $null
    $keyPassword = $null
}

Write-Host ''
Write-Host 'Verifying the four GitHub secret NAMES (values remain hidden)...'
$listed = @(& gh secret list --repo $Repository --app actions --json name --jq '.[].name')
if ($LASTEXITCODE -ne 0) { throw 'Could not verify repository secrets.' }
$required = @('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
              'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
foreach ($name in $required) {
    if ($name -notin $listed) { throw "Missing secret: $name" }
    Write-Host "  OK: $name"
}

Write-Warning "BACK UP YOUR RELEASE KEYSTORE: $KeystorePath"
Write-Warning 'Store its passwords and alias in a password manager.'
Write-Warning 'Losing the key makes future updates under this certificate impossible.'

if (-not $SkipBuild) {
    $publish = if ($PublishRelease) { 'true' } else { 'false' }
    Write-Host 'Triggering a signed release build...'
    & gh workflow run android-release.yml --repo $Repository --ref main -f "publish_release=$publish"
    if ($LASTEXITCODE -ne 0) { throw 'Secrets saved, but workflow dispatch failed.' }
    Write-Host "Track the signed build: https://github.com/$Repository/actions/workflows/android-release.yml"
    if ($PublishRelease) {
        Write-Host "Direct APK (after a successful build): https://github.com/$Repository/releases"
    }
} else {
    Write-Host 'Signing secrets saved. Run Android Release APK from GitHub Actions to build.'
}

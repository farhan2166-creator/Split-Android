# On your own Windows PC, configure signing secrets using the private .jks file.
# The keystore is never committed to the repository.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,
    [string]$Repo = "farhan2166-creator/Split-Android",
    [string]$Alias = "split_release"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI is required: https://cli.github.com/"
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Keystore does not exist: $KeystorePath"
}
& gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Sign in with gh auth login and try again."
}
$securePassword = Read-Host "Enter permanent release keystore password" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
if ([string]::IsNullOrWhiteSpace($password)) { throw "Password required." }
if ($password.Contains([char]10) -or $password.Contains([char]13)) {
    throw "Password must not contain line breaks."
}
$bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeystorePath).Path)
$b64 = [Convert]::ToBase64String($bytes)
$secretText = [string]::Join([Environment]::NewLine, @(
    "ANDROID_KEYSTORE_BASE64=$b64",
    "ANDROID_KEYSTORE_PASSWORD=$password",
    "ANDROID_KEY_ALIAS=$Alias",
    "ANDROID_KEY_PASSWORD=$password"
))
try {
    # gh encrypts values using the repository public key.
    $secretText | & gh secret set --repo $Repo --env-file -
    if ($LASTEXITCODE -ne 0) { throw "Could not set release signing secrets." }
}
finally {
    $password = $null
    $b64 = $null
    $secretText = $null
    [Array]::Clear($bytes,0,$bytes.Length)
}
Write-Host "All four signing secrets uploaded to $Repo."
Write-Host "Run: gh workflow run android-release.yml --repo $Repo --ref main"
Write-Host "Keep your JKS file and password securely backed up."

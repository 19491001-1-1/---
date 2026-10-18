$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    # 与 Shell 版本一致：不递归扫描本地实验产物。
    $paths = @(& git -c core.quotepath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to list repository files.' }
    foreach ($path in $paths) {
        if ($path -match '(^|/)\.DS_Store$|^\.github/modernize/|^(target|out|results)/|^frontend/(node_modules|dist)/|\.(class|log|tmp)$') {
            throw "Generated or local-only file should not be versioned: $path"
        }
    }
    Write-Host 'Repository structure check passed.'
} finally { Pop-Location }

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$excludedDirectories = @('.git', 'target', 'out', 'results', 'node_modules', 'dist', 'coverage', 'playwright-report', 'test-results', '.playwright')
$forbiddenExtensions = @('.pdf', '.docx', '.ppt', '.pptx', '.zip')
$forbiddenNameFragments = @('教程', '答辩', 'explainer')

$forbiddenFiles = Get-ChildItem -LiteralPath $projectRoot -Recurse -File -Force | Where-Object {
    $relativePath = $_.FullName.Substring($projectRoot.Length).TrimStart([char[]]'\/')
    $segments = $relativePath -split '[\\/]'
    $isExcluded = $false
    foreach ($segment in $segments) {
        if ($excludedDirectories -contains $segment) {
            $isExcluded = $true
            break
        }
    }
    if ($isExcluded) { return $false }

    if ($forbiddenExtensions -contains $_.Extension.ToLowerInvariant()) { return $true }
    foreach ($fragment in $forbiddenNameFragments) {
        if ($_.Name.Contains($fragment)) { return $true }
    }
    return $false
}

if ($forbiddenFiles) {
    Write-Error "Repository check failed: reference or tutorial files must stay outside the source repository.`n$($forbiddenFiles.FullName -join "`n")"
}

$dsStore = Get-ChildItem -LiteralPath $projectRoot -Recurse -File -Force -Filter '.DS_Store' | Where-Object {
    $_.FullName -notlike "$projectRoot\.git\*" -and $_.FullName -notlike "$projectRoot/.git/*"
} | Select-Object -First 1
if ($dsStore) { Write-Error 'Repository check failed: .DS_Store files were found.' }

if (Test-Path -LiteralPath (Join-Path $projectRoot '.github/modernize')) {
    Write-Error 'Repository check failed: local modernization tool state must stay outside the repository.'
}

$obsoleteProductDocs = @('PROJECT_STATUS.md', 'docs/开发交接文档.md', 'docs/性能优化记录.md')
foreach ($relativePath in $obsoleteProductDocs) {
    if (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath)) {
        Write-Error "Repository check failed: obsolete process document found: $relativePath"
    }
}

Write-Host 'Repository structure check passed.'

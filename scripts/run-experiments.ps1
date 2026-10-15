param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ExperimentArguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputDirectory = Join-Path $projectRoot 'out'
$sourceList = Join-Path $outputDirectory 'main-sources.txt'

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sources = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notmatch '[\\/]web[\\/]' } |
    ForEach-Object { '"' + $_.FullName + '"' }
[System.IO.File]::WriteAllLines($sourceList, $sources, [System.Text.UTF8Encoding]::new($false))

& javac -encoding UTF-8 -d $outputDirectory "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE." }

if (-not $ExperimentArguments -or $ExperimentArguments.Count -eq 0) {
    Write-Host '未提供参数，执行一次快速演示实验。'
    $ExperimentArguments = @('benchmark', 'gcut01', 'results/demo', '1', '20261012', 'QUICK')
}

& java -cp $outputDirectory com.assessment.packing.experiment.ExperimentMain @ExperimentArguments
if ($LASTEXITCODE -ne 0) { throw "Experiment failed with exit code $LASTEXITCODE." }

param(
    [ValidateSet('claude', 'codex', 'opencode', 'all')]
    [string]$Tool = 'all'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$AgentDir = Join-Path $RepoRoot '.agent'

function New-AgentLink {
    param([string]$Name)

    $Target = Join-Path $RepoRoot ".$Name"
    if (Test-Path $Target) {
        $Item = Get-Item $Target -Force
        if ($Item.LinkType) {
            Remove-Item $Target -Force
        } else {
            Write-Warning "Skip .${Name}: path already exists and is not a link: $Target"
            return
        }
    }

    try {
        New-Item -ItemType SymbolicLink -Path $Target -Target $AgentDir | Out-Null
    } catch {
        New-Item -ItemType Junction -Path $Target -Target $AgentDir | Out-Null
    }
    Write-Host "Linked .$Name -> .agent"
}

if ($Tool -eq 'all') {
    New-AgentLink 'claude'
    New-AgentLink 'codex'
    New-AgentLink 'opencode'
} else {
    New-AgentLink $Tool
}

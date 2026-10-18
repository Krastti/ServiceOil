#requires -Version 7.0
<#
.SYNOPSIS
Выгружает недельные цены Росстата из локальной PostgreSQL для первого запуска.
.EXAMPLE
pwsh -File scripts/export-initial-data.ps1
#>
[CmdletBinding()]
param(
    [string]$Container,
    [string]$OutputFile = (Join-Path $PSScriptRoot '../data/initial-prices.csv')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sql = @'
COPY (
    SELECT r.code AS region_code, r.name AS region_name, f.code AS fuel_code,
           p.observed_on, p.frequency, p.price, p.currency, p.unit, p.source_url
    FROM regional_price p
    JOIN data_source s ON s.id = p.source_id
    JOIN region r ON r.id = p.region_id
    JOIN fuel_type f ON f.id = p.fuel_type_id
    WHERE s.code = 'ROSSTAT_PFO_WEEKLY' AND s.data_mode = 'OPEN_DATA'
      AND p.frequency = 'WEEKLY'
    ORDER BY r.code, f.code, p.observed_on
) TO STDOUT WITH (FORMAT CSV, HEADER, ENCODING 'UTF8');
'@

$dockerArgs = if ($Container) { @('exec', $Container) }
              else { @('compose', '--project-directory', $projectRoot, 'exec', '-T', 'db') }
$dockerArgs += @('psql', '-X', '-U', 'serviceoil', '-d', 'serviceoil', '-v', 'ON_ERROR_STOP=1', '-c', $sql)
# Передаём только SELECT/COPY TO; пароли и внутренние ID в CSV не попадают.
$oldEncoding = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $lines = @(& docker @dockerArgs)
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось выгрузить CSV из PostgreSQL.' }
} finally {
    [Console]::OutputEncoding = $oldEncoding
}
$csv = ($lines -join "`n") + "`n"
$rows = @($csv | ConvertFrom-Csv)
if ($rows.Count -eq 0) { throw 'Недельных цен Росстата нет; существующий CSV оставлен без изменений.' }

$OutputFile = [System.IO.Path]::GetFullPath($OutputFile)
[System.IO.Directory]::CreateDirectory((Split-Path $OutputFile -Parent)) | Out-Null
[System.IO.File]::WriteAllText($OutputFile, $csv, [System.Text.UTF8Encoding]::new($false))
Write-Host "CSV_EXPORTED rows=$($rows.Count) file=$OutputFile"

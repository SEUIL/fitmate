param([Parameter(Mandatory=$true)][string]$Archive, [Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($Archive)
try {
    $reader = [IO.StreamReader]::new($zip.Entries[0].Open(), [Text.Encoding]::GetEncoding(949))
    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $zip.Dispose() }
$records = @{}
foreach ($line in ($text -split "`n" | Select-Object -Skip 1)) {
    $parts = $line.Trim() -split "`t"
    if ($parts.Count -ne 3 -or $parts[2] -ne '존재' -or $parts[0] -notmatch '^\d{8}00$') { continue }
    $records[$parts[0]] = $parts[1].Trim()
}
$provinces = @('서울특별시','부산광역시','대구광역시','인천광역시','광주광역시','대전광역시','울산광역시','세종특별자치시','경기도','강원특별자치도','충청북도','충청남도','전북특별자치도','전라남도','경상북도','경상남도','제주특별자치도')
$ids = @{}
$codesByName = @{}
foreach ($code in $records.Keys) {
    $codesByName[$records[$code]] = $code
    $index = [Array]::IndexOf($provinces, $records[$code])
    if ($index -ge 0) { $ids[$code] = '10000000-0000-0000-0000-{0:D12}' -f ($index + 1) }
    else { $ids[$code] = '11000000-0000-0000-0000-00' + $code }
}
$rows = [Collections.Generic.List[object]]::new()
foreach ($code in ($records.Keys | Sort-Object)) {
    $name = $records[$code]
    if ($name -in $provinces) { continue }
    $parent = $null
    if ($name.Contains(' ')) {
        $parentName = $name.Substring(0, $name.LastIndexOf(' '))
        if (!$codesByName.ContainsKey($parentName)) { throw "Unresolved parent for $code : $name" }
        $parent = $ids[$codesByName[$parentName]]
    }
    $level = if (!$name.Contains(' ')) { 1 } elseif ($code.EndsWith('00000')) { 2 } else { 3 }
    $rows.Add([pscustomobject]@{Code=$code; Name=$name; Level=$level; Depth=($name -split ' ').Count; Parent=$parent; Id=$ids[$code]})
}
if ($rows.Count -lt 3000) { throw 'Incomplete public region dataset' }
$sql = [Text.StringBuilder]::new()
[void]$sql.AppendLine('-- Public legal-dong reference data from https://www.code.go.kr/stdcode/regCodeL.do')
[void]$sql.AppendLine('-- Source archive SHA256: ' + (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash)
[void]$sql.AppendLine('-- Existing province UUIDs remain unchanged; ri-level and abolished entries are excluded.')
[void]$sql.AppendLine('ALTER TABLE core_regions DROP CHECK chk_core_regions_level, ADD CONSTRAINT chk_core_regions_level CHECK (level IN (1, 2, 3));')
foreach ($row in ($rows | Sort-Object Depth,Code)) {
    $escaped = $row.Name.Replace("'", "''")
    $parentSql = if ($row.Parent) { "UUID_TO_BIN('$($row.Parent)',1)" } else { 'NULL' }
    [void]$sql.AppendLine("INSERT INTO core_regions (id,parent_id,name,level,code,is_active,created_at,updated_at) VALUES (UUID_TO_BIN('$($row.Id)',1),$parentSql,'$escaped',$($row.Level),'KR-LAW-$($row.Code)',TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));")
}
for ($index = 0; $index -lt $provinces.Count; $index++) {
    if (!$codesByName.ContainsKey($provinces[$index])) {
        $id = '10000000-0000-0000-0000-{0:D12}' -f ($index + 1)
        [void]$sql.AppendLine("UPDATE core_regions SET is_active=FALSE,updated_at=UTC_TIMESTAMP(3) WHERE id=UUID_TO_BIN('$id',1);")
    }
}
[IO.File]::WriteAllText($Output, $sql.ToString(), [Text.UTF8Encoding]::new($false))
Write-Output "Generated $($rows.Count) regions"

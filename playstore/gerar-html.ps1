# Gera as versoes em HTML (publicadas pelo GitHub Pages a partir de docs/) dos MESMOS textos que o app
# mostra: app/src/main/assets/politica-de-privacidade.md e termos-de-uso.md.
# Uso:  powershell -File playstore\gerar-html.ps1 -Responsavel "Seu Nome" -Contato "voce@exemplo.com"
# Sem parametros, deixa os marcadores [SEU NOME] e [SEU E-MAIL] no HTML.
# Arquivo so com ASCII de proposito (PowerShell 5.1 le script sem BOM como ANSI).
param(
    [string]$Responsavel = "[SEU NOME]",
    [string]$Contato = "[SEU E-MAIL]"
)

$utf8 = New-Object System.Text.UTF8Encoding($false)
$raiz = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $raiz "app\src\main\assets"
$docs = Join-Path $raiz "docs"
New-Item -ItemType Directory -Force $docs | Out-Null

function Esc([string]$t) { $t.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;") }
function Inline([string]$t) {
    $t = Esc $t
    $t = [regex]::Replace($t, '\*\*(.+?)\*\*', '<strong>$1</strong>')
    $t = [regex]::Replace($t, '`(.+?)`', '<code>$1</code>')
    return $t
}

function ConverterDocumento([string]$arquivo, [string]$saida) {
    $md = [System.IO.File]::ReadAllText((Join-Path $assets $arquivo), $utf8)
    $md = $md.Replace("{{RESPONSAVEL}}", $Responsavel).Replace("{{CONTATO}}", $Contato)
    $titulo = ""
    $corpo = New-Object System.Text.StringBuilder
    $emLista = $false
    foreach ($linha in ($md -split "`r?`n")) {
        $l = $linha.Trim()
        if ($l -eq "") { continue }
        if ($l.StartsWith("- ")) {
            if (-not $emLista) { [void]$corpo.AppendLine("<ul>"); $emLista = $true }
            [void]$corpo.AppendLine("  <li>" + (Inline $l.Substring(2).Trim()) + "</li>")
            continue
        }
        if ($emLista) { [void]$corpo.AppendLine("</ul>"); $emLista = $false }
        if ($l.StartsWith("## ")) { [void]$corpo.AppendLine("<h2>" + (Inline $l.Substring(3).Trim()) + "</h2>") }
        elseif ($l.StartsWith("# ")) { $titulo = $l.Substring(2).Trim(); [void]$corpo.AppendLine("<h1>" + (Inline $titulo) + "</h1>") }
        else { [void]$corpo.AppendLine("<p>" + (Inline $l) + "</p>") }
    }
    if ($emLista) { [void]$corpo.AppendLine("</ul>") }

    $html = @"
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$(Esc $titulo)</title>
<style>
  body { font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; max-width: 720px; margin: 0 auto; padding: 24px 16px 64px; color: #1f2937; background: #ffffff; }
  h1 { font-size: 1.6rem; } h2 { font-size: 1.15rem; margin-top: 2rem; }
  code { background: #f3f4f6; padding: 1px 5px; border-radius: 4px; }
  @media (prefers-color-scheme: dark) { body { background: #0c1014; color: #e5e7eb; } code { background: #1f2937; } }
</style>
</head>
<body>
$($corpo.ToString())</body>
</html>
"@
    [System.IO.File]::WriteAllText((Join-Path $docs $saida), $html, $utf8)
    Write-Output ("gerado: " + $saida)
}

ConverterDocumento "politica-de-privacidade.md" "politica-de-privacidade.html"
ConverterDocumento "termos-de-uso.md" "termos-de-uso.html"

# Pagina inicial do site (so ASCII: acentos como entidades HTML)
$index = @"
<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bloqueador de Reels</title>
<style>
  body { font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; max-width: 720px; margin: 0 auto; padding: 32px 16px 64px; color: #1f2937; background: #ffffff; }
  h1 { font-size: 1.8rem; margin-bottom: 0; } p.sub { color: #6b7280; margin-top: 4px; }
  a { color: #3b4a7a; } li { margin: 6px 0; }
  @media (prefers-color-scheme: dark) { body { background: #0c1014; color: #e5e7eb; } p.sub { color: #9ca3af; } a { color: #aab8f0; } }
</style>
</head>
<body>
<h1>Bloqueador de Reels</h1>
<p class="sub">Instagram sem a aba Reels</p>
<p>App Android que esconde a aba Reels do Instagram e volta sozinho se o Reels abrir por outro caminho. Tudo roda no seu celular; o app n&atilde;o tem acesso &agrave; internet.</p>
<ul>
  <li><a href="politica-de-privacidade.html">Pol&iacute;tica de privacidade</a></li>
  <li><a href="termos-de-uso.html">Termos de uso</a></li>
</ul>
<p>Respons&aacute;vel: $(Esc $Responsavel) &middot; Contato: $(Esc $Contato)</p>
<p class="sub">App independente, sem afilia&ccedil;&atilde;o com o Instagram ou a Meta.</p>
</body>
</html>
"@
[System.IO.File]::WriteAllText((Join-Path $docs "index.html"), $index, $utf8)
Write-Output "gerado: index.html"

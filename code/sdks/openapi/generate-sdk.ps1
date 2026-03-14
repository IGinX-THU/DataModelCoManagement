$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "../..")
$specPath = Resolve-Path (Join-Path $repoRoot "iginx-assoc-backend/docs/openapi.yaml")
$javaOut = Join-Path $repoRoot "sdks/openapi/generated/java"
$pythonOut = Join-Path $repoRoot "sdks/openapi/generated/python"

if (!(Get-Command "openapi-generator-cli" -ErrorAction SilentlyContinue)) {
    throw "未找到 openapi-generator-cli，请先安装后重试。"
}

Write-Host "生成 Java 基础客户端..."
openapi-generator-cli generate `
  -i $specPath `
  -g java `
  -o $javaOut `
  -c (Join-Path $scriptDir "java-config.json")

Write-Host "生成 Python 基础客户端..."
openapi-generator-cli generate `
  -i $specPath `
  -g python `
  -o $pythonOut `
  -c (Join-Path $scriptDir "python-config.json")

Write-Host "SDK 生成完成。"

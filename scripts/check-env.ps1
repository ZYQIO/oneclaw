param(
    [int]$BrokerPort = 8080,
    [switch]$SkipBroker
)

$ErrorActionPreference = "Stop"

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    [pscustomobject]@{
        Name   = $Name
        Status = $Status
        Detail = $Detail
    }
}

function Get-CommandInfo {
    param([string]$Name)

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        return $null
    }

    return [pscustomobject]@{
        Name   = $command.Name
        Source = $command.Source
    }
}

function Test-Broker {
    param(
        [string]$NodePath,
        [string]$BrokerDir,
        [int]$Port
    )

    $script = @'
const { spawn } = require("node:child_process");
const http = require("node:http");

const brokerDir = process.argv[2];
const port = Number(process.argv[3]);
const child = spawn(process.execPath, ["server.mjs"], {
  cwd: brokerDir,
  stdio: ["ignore", "pipe", "pipe"],
  windowsHide: true,
});

let stdout = "";
let stderr = "";
child.stdout.on("data", (chunk) => stdout += chunk.toString());
child.stderr.on("data", (chunk) => stderr += chunk.toString());

function request(path) {
  return new Promise((resolve, reject) => {
    const req = http.get({ hostname: "127.0.0.1", port, path }, (res) => {
      let data = "";
      res.on("data", (chunk) => data += chunk);
      res.on("end", () => resolve({ status: res.statusCode, body: data }));
    });
    req.on("error", reject);
  });
}

(async () => {
  try {
    for (let i = 0; i < 20; i += 1) {
      try {
        const health = await request("/healthz");
        const state = await request("/api/state");
        const index = await request("/");
        console.log(JSON.stringify({
          ok: true,
          pid: child.pid,
          healthStatus: health.status,
          healthBody: health.body,
          stateStatus: state.status,
          stateBody: state.body,
          indexStatus: index.status,
          startupLog: stdout.trim(),
          stderr: stderr.trim()
        }));
        child.kill();
        return;
      } catch (error) {
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
    }

    console.log(JSON.stringify({
      ok: false,
      pid: child.pid,
      startupLog: stdout.trim(),
      stderr: stderr.trim(),
      error: "broker_not_ready"
    }));
    child.kill();
    process.exitCode = 1;
  } catch (error) {
    console.log(JSON.stringify({
      ok: false,
      pid: child.pid,
      startupLog: stdout.trim(),
      stderr: stderr.trim(),
      error: error.message
    }));
    child.kill();
    process.exitCode = 1;
  }
})();
'@

    $json = $script | & $NodePath - $BrokerDir $Port
    return $json | ConvertFrom-Json
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$results = @()

$java = Get-CommandInfo "java"
$adb = Get-CommandInfo "adb"
$node = Get-CommandInfo "node"
$npm = Get-CommandInfo "npm"

$results += if ($java) {
    $version = & $java.Source -version 2>&1 | Select-Object -First 1
    Add-Result "java" "PASS" "$version ($($java.Source))"
} else {
    Add-Result "java" "FAIL" "java not found on PATH"
}

$results += if ($adb) {
    $version = & $adb.Source version 2>&1 | Select-Object -First 1
    Add-Result "adb" "PASS" "$version ($($adb.Source))"
} else {
    Add-Result "adb" "FAIL" "adb not found on PATH"
}

$results += if ($node) {
    $version = & $node.Source -v
    Add-Result "node" "PASS" "$version ($($node.Source))"
} else {
    Add-Result "node" "FAIL" "node not found on PATH"
}

$results += if ($npm) {
    $version = & $npm.Source -v
    Add-Result "npm" "PASS" "$version ($($npm.Source))"
} else {
    Add-Result "npm" "FAIL" "npm not found on PATH"
}

$javaHome = $env:JAVA_HOME
$androidHome = $env:ANDROID_HOME
$androidSdkRoot = $env:ANDROID_SDK_ROOT
$localPropertiesPath = Join-Path $repoRoot "local.properties"

$results += if ($javaHome) {
    Add-Result "JAVA_HOME" "PASS" $javaHome
} else {
    Add-Result "JAVA_HOME" "FAIL" "JAVA_HOME is not set"
}

$results += if ($androidHome) {
    Add-Result "ANDROID_HOME" "PASS" $androidHome
} else {
    Add-Result "ANDROID_HOME" "FAIL" "ANDROID_HOME is not set"
}

$results += if ($androidSdkRoot) {
    Add-Result "ANDROID_SDK_ROOT" "PASS" $androidSdkRoot
} else {
    Add-Result "ANDROID_SDK_ROOT" "FAIL" "ANDROID_SDK_ROOT is not set"
}

$results += if (Test-Path $localPropertiesPath) {
    Add-Result "local.properties" "PASS" $localPropertiesPath
} else {
    Add-Result "local.properties" "FAIL" "local.properties is missing"
}

$brokerDir = Join-Path $repoRoot "remote-broker"
$brokerEntry = Join-Path $brokerDir "server.mjs"
$brokerWs = Join-Path $brokerDir "node_modules/ws/package.json"

$results += if (Test-Path $brokerEntry) {
    Add-Result "remote-broker entry" "PASS" $brokerEntry
} else {
    Add-Result "remote-broker entry" "FAIL" "remote-broker/server.mjs is missing"
}

$results += if (Test-Path $brokerWs) {
    Add-Result "remote-broker ws dependency" "PASS" $brokerWs
} else {
    Add-Result "remote-broker ws dependency" "FAIL" "remote-broker/node_modules/ws is missing"
}

if (-not $SkipBroker) {
    if ($node -and (Test-Path $brokerEntry)) {
        try {
            $broker = Test-Broker -NodePath $node.Source -BrokerDir $brokerDir -Port $BrokerPort
            if ($broker.ok -and $broker.healthStatus -eq 200 -and $broker.stateStatus -eq 200 -and $broker.indexStatus -eq 200) {
                $detail = "health=$($broker.healthStatus), state=$($broker.stateStatus), index=$($broker.indexStatus), log=$($broker.startupLog)"
                $results += Add-Result "remote-broker self-test" "PASS" $detail
            } else {
                $detail = "error=$($broker.error); stderr=$($broker.stderr); log=$($broker.startupLog)"
                $results += Add-Result "remote-broker self-test" "FAIL" $detail
            }
        } catch {
            $results += Add-Result "remote-broker self-test" "FAIL" $_.Exception.Message
        }
    } else {
        $results += Add-Result "remote-broker self-test" "SKIP" "node or broker entry is unavailable"
    }
}

Write-Host ""
Write-Host "OneClaw environment check"
Write-Host "Repo root: $repoRoot"
Write-Host ""
$results | Format-Table -AutoSize

$failCount = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
$skipCount = ($results | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host ""
Write-Host "Summary: FAIL=$failCount SKIP=$skipCount"

if ($failCount -gt 0) {
    exit 1
}

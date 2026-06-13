$env:AI_API_KEY="82f20bac24a545c595dc30f7eee3dfd1.eJmxuTgiNAxaDNEX"
$cp = Get-Content -Path "backend/target/cp.txt" -Raw
$cp = $cp.Trim()
$javaPath = "java"
$classPath = "backend/target/classes;$cp"
$workingDir = "D:\Codex Web\20252160A1025游源"
Start-Process -FilePath $javaPath -ArgumentList "-cp", "`"$classPath`"", "com.onlineexam.OnlineExamApplication" -WorkingDirectory $workingDir -NoNewWindow -Wait

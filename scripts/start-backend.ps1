$env:AI_API_KEY='82f20bac24a545c595dc30f7eee3dfd1.eJmxuTgiNAxaDNEX'
$cp = Get-Content -Raw 'backend/target/cp.txt'
$cp = $cp.Trim()
$fullCp = "backend/target/classes;$cp"
Start-Process -FilePath 'java' -ArgumentList "-cp", $fullCp, "com.onlineexam.OnlineExamApplication" -NoNewWindow
